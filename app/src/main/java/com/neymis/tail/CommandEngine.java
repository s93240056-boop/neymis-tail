package com.neymis.tail;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CommandEngine {

    interface Host {
        android.content.Context context();

        void share(File file);

        void open(File file);

        void toast(String message);

        void clear();

        void applyTheme(int theme);

        void setColor(String code);

        void exit();
    }

    private final Host host;
    private final File root;
    private File cwd;
    private final List<String> history = new ArrayList<>();
    private final Map<String, String> env = new HashMap<>();

    private static final Map<String, String> HELP = new HashMap<>();

    static {
        HELP.put("help", "help [команда] — список команд или справка по команде");
        HELP.put("ls", "ls [путь] — список файлов и папок");
        HELP.put("cd", "cd [путь] — перейти в папку (.. — наверх)");
        HELP.put("pwd", "pwd — текущий путь");
        HELP.put("cat", "cat <файл> — вывести текст файла");
        HELP.put("find", "find <имя> — поиск по всему хранилищу");
        HELP.put("grep", "grep <текст> <файл> — поиск внутри файла");
        HELP.put("head", "head <файл> [n] — первые строки (по умолчанию 10)");
        HELP.put("tail", "tail <файл> [n] — последние строки");
        HELP.put("wc", "wc <файл> — число строк и размер");
        HELP.put("mkdir", "mkdir <имя> — создать папку");
        HELP.put("touch", "touch <имя> — создать пустой файл");
        HELP.put("cp", "cp <откуда> <куда> — копировать");
        HELP.put("mv", "mv <откуда> <куда> — переместить");
        HELP.put("rm", "rm <путь> — удалить в корзину .NeymisTailTrash");
        HELP.put("zip", "zip <путь> [выход.zip] — создать архив");
        HELP.put("unzip", "unzip <архив> [папка] — распаковать");
        HELP.put("hash", "hash <файл> — MD5 и SHA-256");
        HELP.put("stat", "stat <файл> — подробности файла");
        HELP.put("tree", "tree [путь] — дерево папок");
        HELP.put("share", "share <файл> — поделиться");
        HELP.put("open", "open <файл> — открыть просмотрщиком");
        HELP.put("df", "df — свободное место на хранилище");
        HELP.put("apps", "apps [фильтр] — список установленных приложений");
        HELP.put("history", "history — история команд");
        HELP.put("echo", "echo <текст> — вывести текст");
        HELP.put("date", "date — текущая дата и время");
        HELP.put("whoami", "whoami — информацию о пользователе");
        HELP.put("theme", "theme [gradient|light|dark] — тема оформления");
        HELP.put("run", "run <файл> — выполнить команды из файла");
        HELP.put("clear", "cls / clear — очистить экран");
        HELP.put("ren", "ren <файл> <новое имя> — переименовать");
        HELP.put("attrib", "attrib <файл> — атрибуты файла");
        HELP.put("where", "where <имя> — найти файл по имени");
        HELP.put("ipconfig", "ipconfig [/all] — сетевые адреса и MAC");
        HELP.put("ping", "ping <хост> — проверить доступность");
        HELP.put("nslookup", "nslookup <домен> — запрос DNS");
        HELP.put("netstat", "netstat — активные подключения и порты");
        HELP.put("arp", "arp — таблица IP ↔ MAC");
        HELP.put("getmac", "getmac — MAC-адреса адаптеров");
        HELP.put("route", "route — таблица маршрутизации");
        HELP.put("tasklist", "tasklist — запущенные процессы");
        HELP.put("start", "start <пакет> — запустить приложение");
        HELP.put("vol", "vol — метки и размеры хранилищ");
        HELP.put("systeminfo", "systeminfo — сводка об устройстве");
        HELP.put("ver", "ver — версия Android");
        HELP.put("color", "color <код> — цвет текста (0a, 0c, 0f)");
        HELP.put("set", "set [имя=значение] — переменные окружения");
        HELP.put("sleep", "sleep <сек> — пауза");
        HELP.put("exit", "exit — выйти из приложения");
    }

    CommandEngine(Host host, File root) {
        this.host = host;
        this.root = root;
        this.cwd = root;
    }

    File cwd() {
        return cwd;
    }

    List<String> history() {
        return history;
    }

    List<String> completions(String prefix) {
        List<String> result = new ArrayList<>();
        if (!prefix.contains(" ")) {
            for (String command : HELP.keySet()) {
                if (command.startsWith(prefix.toLowerCase())) result.add(command);
            }
            return result;
        }
        int space = prefix.lastIndexOf(' ') + 1;
        String token = prefix.substring(space);
        File[] children = cwd.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.getName().startsWith(token)) {
                    result.add(prefix.substring(0, space) + child.getName());
                }
            }
        }
        return result;
    }

    String run(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return "";
        history.add(trimmed);
        List<String> parts = tokenize(trimmed);
        String command = parts.get(0).toLowerCase();
        command = switch (command) {
            case "dir" -> "ls";
            case "md" -> "mkdir";
            case "rd", "rmdir" -> "rm";
            case "del", "erase" -> "rm";
            case "type" -> "cat";
            case "findstr" -> "grep";
            case "ren", "rename" -> "ren";
            case "cls" -> "clear";
            case "ver" -> "ver";
            default -> command;
        };
        List<String> args = new ArrayList<>();
        for (String part : parts.subList(1, parts.size())) {
            String expanded = part;
            for (Map.Entry<String, String> entry : env.entrySet()) {
                expanded = expanded.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            args.add(expanded);
        }

        try {
            return switch (command) {
                case "help", "man" -> help(args);
                case "ls" -> ls(args);
                case "cd" -> cd(args);
                case "pwd" -> cwd.getAbsolutePath();
                case "cat" -> cat(args);
                case "find" -> find(args);
                case "grep" -> grep(args);
                case "head" -> head(args, true);
                case "tail" -> head(args, false);
                case "wc" -> wc(args);
                case "mkdir" -> mkdir(args);
                case "touch" -> touch(args);
                case "cp" -> cp(args);
                case "mv" -> mv(args);
                case "rm" -> rm(args);
                case "zip" -> zip(args);
                case "unzip" -> unzip(args);
                case "hash" -> hash(args);
                case "stat" -> stat(args);
                case "tree" -> tree(args);
                case "share" -> external(args, true);
                case "open" -> external(args, false);
                case "df" -> df();
                case "apps" -> apps(args);
                case "history" -> historyText();
                case "echo" -> String.join(" ", args);
                case "date" -> new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                        .format(new Date());
                case "whoami" -> "Neymis Tail 1.0 · " + root.getAbsolutePath()
                        + "\nПакет: com.neymis.tail · без root, без сети";
                case "theme" -> theme(args);
                case "run" -> runScript(args);
                case "clear" -> {
                    host.clear();
                    yield "";
                }
                case "ren" -> rename(args);
                case "attrib" -> attrib(args);
                case "where" -> where(args);
                case "ipconfig" -> ipconfig(args);
                case "ping" -> ping(args);
                case "nslookup" -> nslookup(args);
                case "netstat" -> netstat();
                case "arp" -> arp();
                case "getmac" -> getmac();
                case "route" -> route();
                case "tasklist" -> tasklist();
                case "start" -> startApp(args);
                case "vol" -> vol();
                case "systeminfo" -> systemInfo();
                case "ver" -> "Android " + android.os.Build.VERSION.RELEASE
                        + " (API " + android.os.Build.VERSION.SDK_INT + ")";
                case "color" -> color(args);
                case "set" -> setEnv(args);
                case "sleep" -> sleep(args);
                case "exit" -> {
                    host.exit();
                    yield "Выход...";
                }
                default -> "Неизвестная команда: " + command + "\nВведи `help` для списка команд.";
            };
        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
    }

    private static List<String> tokenize(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quote = !quote;
            } else if (c == ' ' && !quote) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        if (result.isEmpty()) result.add("");
        return result;
    }

    private File resolve(String path) {
        File file = new File(path);
        return file.isAbsolute() ? file : new File(cwd, path);
    }

    private String require(List<String> args, int index, String usage) throws IOException {
        if (args.size() <= index) throw new IOException("Использование: " + usage);
        return args.get(index);
    }

    private String help(List<String> args) {
        if (args.isEmpty()) {
            List<String> names = new ArrayList<>(HELP.keySet());
            Collections.sort(names);
            return "Команды Neymis Tail:\n\n" + String.join("\n", names)
                    + "\n\n`help <команда>` — подробная справка";
        }
        String key = args.get(0).toLowerCase();
        String text = HELP.get(key);
        return text == null ? "Нет справки по команде: " + key : text;
    }

    private String ls(List<String> args) {
        File dir = args.isEmpty() ? cwd : resolve(args.get(0));
        File[] children = dir.listFiles();
        if (children == null) return "Не удалось прочитать: " + dir.getAbsolutePath();
        List<String> lines = new ArrayList<>();
        for (File child : children) {
            lines.add((child.isDirectory() ? "d " : "- ") + Fs.human(
                    child.isDirectory() ? Fs.size(child) : child.length())
                    + "  " + child.getName());
        }
        Collections.sort(lines);
        if (lines.isEmpty()) return "(пусто)";
        return String.join("\n", lines);
    }

    private String cd(List<String> args) throws IOException {
        String target = require(args, 0, "cd <путь>");
        File dir = resolve(target);
        if (!dir.isDirectory()) throw new IOException("Не папка: " + target);
        cwd = dir;
        return cwd.getAbsolutePath();
    }

    private String cat(List<String> args) throws IOException {
        File file = resolve(require(args, 0, "cat <файл>"));
        if (!file.isFile()) throw new IOException("Нет файла: " + file.getName());
        return new String(Fs.readAll(file), "UTF-8");
    }

    private String find(List<String> args) throws IOException {
        String query = require(args, 0, "find <имя>");
        List<File> result = Fs.search(root, query, 200);
        if (result.isEmpty()) return "Ничего не найдено";
        StringBuilder out = new StringBuilder();
        for (File file : result) out.append(file.getAbsolutePath()).append('\n');
        return out.toString().trim();
    }

    private String grep(List<String> args) throws IOException {
        if (args.size() < 2) throw new IOException("Использование: grep <текст> <файл>");
        File file = resolve(args.get(1));
        List<String> lines = Fs.readLines(file);
        List<String> found = new ArrayList<>();
        String needle = args.get(0).toLowerCase();
        for (String line : lines) {
            if (line.toLowerCase().contains(needle)) found.add(line);
        }
        return found.isEmpty() ? "Совпадений нет" : String.join("\n", found);
    }

    private String head(List<String> args, boolean first) throws IOException {
        File file = resolve(require(args, 0, (first ? "head" : "tail") + " <файл> [n]"));
        List<String> lines = Fs.readLines(file);
        int count = 10;
        if (args.size() > 1) count = Integer.parseInt(args.get(1));
        count = Math.min(count, lines.size());
        List<String> selected = first ? lines.subList(0, count)
                : lines.subList(lines.size() - count, lines.size());
        return String.join("\n", selected);
    }

    private String wc(List<String> args) throws IOException {
        File file = resolve(require(args, 0, "wc <файл>"));
        List<String> lines = Fs.readLines(file);
        return lines.size() + " строк · " + Fs.human(file.length());
    }

    private String mkdir(List<String> args) throws IOException {
        String name = require(args, 0, "mkdir <имя>");
        File dir = resolve(name);
        if (!dir.mkdirs()) throw new IOException("Не удалось создать: " + name);
        return "Создано: " + dir.getAbsolutePath();
    }

    private String touch(List<String> args) throws IOException {
        String name = require(args, 0, "touch <имя>");
        File file = resolve(name);
        if (!file.createNewFile()) throw new IOException("Файл уже есть: " + name);
        return "Создан: " + file.getAbsolutePath();
    }

    private String cp(List<String> args) throws IOException {
        if (args.size() < 2) throw new IOException("Использование: cp <откуда> <куда>");
        File src = resolve(args.get(0));
        File dst = resolve(args.get(1));
        if (!Fs.copy(src, dst)) throw new IOException("Не удалось скопировать");
        return "Скопировано → " + dst.getAbsolutePath();
    }

    private String mv(List<String> args) throws IOException {
        if (args.size() < 2) throw new IOException("Использование: mv <откуда> <куда>");
        File src = resolve(args.get(0));
        File dst = resolve(args.get(1));
        if (!Fs.move(src, dst)) throw new IOException("Не удалось переместить");
        return "Перемещено → " + dst.getAbsolutePath();
    }

    private String rm(List<String> args) throws IOException {
        File file = resolve(require(args, 0, "rm <путь>"));
        File trash = new File(root, ".NeymisTailTrash");
        if (!trash.isDirectory()) trash.mkdirs();
        File target = Fs.unique(trash, file.getName());
        if (!Fs.move(file, target)) throw new IOException("Не удалось удалить");
        return "В корзине: " + target.getAbsolutePath();
    }

    private String zip(List<String> args) throws IOException {
        String source = require(args, 0, "zip <путь> [выход.zip]");
        File dir = resolve(source);
        File output = args.size() > 1 ? resolve(args.get(1))
                : new File(cwd, dir.getName() + ".zip");
        Fs.zip(dir, output);
        return "Архив: " + output.getAbsolutePath() + " (" + Fs.human(output.length()) + ")";
    }

    private String unzip(List<String> args) throws IOException {
        String archiveName = require(args, 0, "unzip <архив> [папка]");
        File archive = resolve(archiveName);
        File destination = args.size() > 1 ? resolve(args.get(1))
                : Fs.unique(archive.getParentFile(),
                archive.getName().replaceAll("\\.zip$", ""));
        int count = Fs.unzip(archive, destination);
        return "Распаковано файлов: " + count + " → " + destination.getAbsolutePath();
    }

    private String hash(List<String> args) throws Exception {
        File file = resolve(require(args, 0, "hash <файл>"));
        return "MD5:    " + Fs.hash(file, "MD5")
                + "\nSHA-256: " + Fs.hash(file, "SHA-256");
    }

    private String stat(List<String> args) throws IOException {
        File file = resolve(require(args, 0, "stat <файл>"));
        String date = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(new Date(file.lastModified()));
        return "Имя: " + file.getName()
                + "\nПуть: " + file.getAbsolutePath()
                + "\nТип: " + (file.isDirectory() ? "папка" : "файл")
                + "\nРазмер: " + Fs.human(Fs.size(file))
                + "\nИзменён: " + date
                + "\nЧтение: " + file.canRead() + " · Запись: " + file.canWrite();
    }

    private String tree(List<String> args) {
        File dir = args.isEmpty() ? cwd : resolve(args.get(0));
        StringBuilder out = new StringBuilder(dir.getAbsolutePath());
        treeLevel(dir, "", 3, out);
        return out.toString();
    }

    private void treeLevel(File dir, String indent, int depth, StringBuilder out) {
        if (depth <= 0) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        int shown = 0;
        for (File child : children) {
            if (shown++ >= 40) {
                out.append(indent).append("…\n");
                break;
            }
            out.append('\n').append(indent).append(child.isDirectory() ? "├─ " : "└─ ")
                    .append(child.getName());
            if (child.isDirectory()) treeLevel(child, indent + "   ", depth - 1, out);
        }
    }

    private String external(List<String> args, boolean share) throws IOException {
        File file = resolve(require(args, 0, (share ? "share" : "open") + " <файл>"));
        if (!file.exists()) throw new IOException("Нет файла: " + file.getName());
        if (share) host.share(file);
        else host.open(file);
        return (share ? "Отправка через «Поделиться»: " : "Открытие: ") + file.getAbsolutePath();
    }

    private String df() {
        android.os.StatFs stat = new android.os.StatFs(root.getPath());
        long total = stat.getTotalBytes();
        long available = stat.getAvailableBytes();
        return "Всего: " + Fs.human(total)
                + "\nСвободно: " + Fs.human(available)
                + "\nЗанято: " + Fs.human(total - available);
    }

    private String apps(List<String> args) {
        List<ApplicationInfo> installed = host.context().getPackageManager()
                .getInstalledApplications(0);
        String filter = args.isEmpty() ? "" : args.get(0).toLowerCase();
        List<String> labels = new ArrayList<>();
        for (ApplicationInfo app : installed) {
            String label = host.context().getPackageManager().getApplicationLabel(app).toString();
            if (filter.isEmpty() || label.toLowerCase().contains(filter)
                    || app.packageName.toLowerCase().contains(filter)) {
                labels.add(label + " · " + app.packageName);
            }
        }
        Collections.sort(labels);
        if (labels.size() > 100) labels = labels.subList(0, 100);
        return labels.isEmpty() ? "Ничего не найдено" : String.join("\n", labels);
    }

    private String historyText() {
        if (history.isEmpty()) return "История пуста";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            out.append(i + 1).append("  ").append(history.get(i)).append('\n');
        }
        return out.toString().trim();
    }

    private String theme(List<String> args) {
        if (args.isEmpty()) return "Темы: gradient, light, dark";
        int value = switch (args.get(0).toLowerCase()) {
            case "light" -> 1;
            case "dark" -> 2;
            case "gradient" -> 0;
            default -> -1;
        };
        if (value < 0) return "Неизвестная тема: " + args.get(0);
        host.applyTheme(value);
        return "Тема: " + args.get(0);
    }

    private String runScript(List<String> args) throws IOException {
        File file = resolve(require(args, 0, "run <файл>"));
        List<String> lines = Fs.readLines(file);
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            out.append("$ ").append(line).append('\n').append(run(line)).append('\n');
        }
        return out.toString().trim();
    }

    private String rename(List<String> args) throws IOException {
        if (args.size() < 2) throw new IOException("Использование: ren <файл> <новое имя>");
        File file = resolve(args.get(0));
        File target = new File(file.getParentFile(), args.get(1));
        if (!file.renameTo(target)) throw new IOException("Не удалось переименовать");
        return "Переименовано: " + target.getName();
    }

    private String attrib(List<String> args) throws IOException {
        File file = resolve(require(args, 0, "attrib <файл>"));
        return file.getName()
                + "\nТип: " + (file.isDirectory() ? "папка" : "файл")
                + "\nЧтение: " + file.canRead()
                + "\nЗапись: " + file.canWrite()
                + "\nИсполнение: " + file.canExecute()
                + "\nСкрытый: " + file.getName().startsWith(".");
    }

    private String where(List<String> args) throws IOException {
        String name = require(args, 0, "where <имя>");
        List<File> found = Fs.search(root, name, 100);
        if (found.isEmpty()) return "Ничего не найдено";
        StringBuilder out = new StringBuilder();
        for (File file : found) out.append(file.getAbsolutePath()).append('\n');
        return out.toString().trim();
    }

    private String ipconfig(List<String> args) {
        StringBuilder out = new StringBuilder();
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (!args.contains("/all") && (ni.isLoopback() || !ni.isUp())) continue;
                out.append(ni.getName()).append("  ").append(ni.getDisplayName()).append('\n');
                java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    out.append("  IP: ").append(address.getHostAddress()).append('\n');
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac != null) {
                    StringBuilder hex = new StringBuilder();
                    for (byte b : mac) hex.append(String.format("%02X-", b));
                    if (hex.length() > 0) hex.setLength(hex.length() - 1);
                    out.append("  MAC: ").append(hex).append('\n');
                }
            }
        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
        return out.length() == 0 ? "Сетевые интерфейсы не найдены" : out.toString().trim();
    }

    private String ping(List<String> args) throws IOException {
        String host = require(args, 0, "ping <хост>");
        StringBuilder out = new StringBuilder("Ping " + host + ":\n");
        try {
            java.net.InetAddress address = java.net.InetAddress.getByName(host);
            out.append("Адрес: ").append(address.getHostAddress()).append('\n');
            for (int i = 0; i < 4; i++) {
                long start = System.currentTimeMillis();
                boolean reachable = address.isReachable(1500);
                long ms = System.currentTimeMillis() - start;
                out.append(reachable ? "Ответ от " : "Таймаут: ")
                        .append(address.getHostAddress()).append(": ")
                        .append(ms).append(" мс\n");
            }
        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
        return out.toString().trim();
    }

    private String nslookup(List<String> args) throws IOException {
        String host = require(args, 0, "nslookup <домен>");
        try {
            java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(host);
            StringBuilder out = new StringBuilder("DNS: " + host + "\n");
            for (java.net.InetAddress address : addresses) {
                out.append(address.getHostAddress()).append('\n');
            }
            return out.toString().trim();
        } catch (Exception e) {
            return "Ошибка DNS: " + e.getMessage();
        }
    }

    private String netstat() {
        StringBuilder out = new StringBuilder("Активные соединения (tcp):\n");
        try {
            for (String fileName : new String[]{"/proc/net/tcp", "/proc/net/tcp6"}) {
                List<String> lines = Fs.readLines(new File(fileName));
                for (int i = 1; i < lines.size() && i < 60; i++) {
                    String[] parts = lines.get(i).trim().split("\\s+");
                    if (parts.length < 4) continue;
                    String[] local = parts[1].split(":");
                    int port = Integer.parseInt(local[1], 16);
                    int state = Integer.parseInt(parts[3], 16);
                    String status = state == 1 ? "ESTABLISHED"
                            : state == 2 ? "SYN_SENT" : state == 10 ? "LISTEN" : "state=" + state;
                    out.append("tcp  :").append(port).append("  ").append(status).append('\n');
                }
            }
        } catch (Exception e) {
            return "Не удалось прочитать /proc/net: " + e.getMessage();
        }
        return out.toString().trim();
    }

    private String arp() {
        try {
            List<String> lines = Fs.readLines(new File("/proc/net/arp"));
            if (lines.isEmpty()) return "Таблица ARP пуста";
            return String.join("\n", lines).trim();
        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
    }

    private String getmac() {
        StringBuilder out = new StringBuilder();
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                byte[] mac = ni.getHardwareAddress();
                if (mac == null) continue;
                StringBuilder hex = new StringBuilder();
                for (byte b : mac) hex.append(String.format("%02X-", b));
                hex.setLength(hex.length() - 1);
                out.append(ni.getName()).append(": ").append(hex).append('\n');
            }
        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
        return out.length() == 0 ? "MAC не найден" : out.toString().trim();
    }

    private String route() {
        try {
            List<String> lines = Fs.readLines(new File("/proc/net/route"));
            if (lines.isEmpty()) return "Таблица маршрутов пуста";
            return String.join("\n", lines).trim();
        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
    }

    private String tasklist() {
        try {
            android.app.ActivityManager manager = (android.app.ActivityManager)
                    host.context().getSystemService(Context.ACTIVITY_SERVICE);
            List<android.app.ActivityManager.RunningAppProcessInfo> processes =
                    manager.getRunningAppProcesses();
            if (processes == null) return "Список процессов недоступен";
            StringBuilder out = new StringBuilder("PID    PROCESS\n");
            for (android.app.ActivityManager.RunningAppProcessInfo info : processes) {
                out.append(info.pid).append("  ").append(info.processName).append('\n');
            }
            return out.toString().trim();
        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
    }

    private String startApp(List<String> args) throws IOException {
        String name = require(args, 0, "start <пакет или имя>");
        android.content.Intent intent = host.context().getPackageManager()
                .getLaunchIntentForPackage(name);
        if (intent == null) {
            android.content.Intent search = host.context().getPackageManager()
                    .getLaunchIntentForPackage(name.contains(".") ? name : "com.neymis." + name);
            intent = search;
        }
        if (intent == null) return "Приложение не найдено: " + name;
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        host.context().startActivity(intent);
        return "Запуск: " + name;
    }

    private String vol() {
        StringBuilder out = new StringBuilder();
        File[] roots = {new File("/storage/emulated/0"), root, getExternalFilesDir()};
        for (File path : roots) {
            if (path == null || !path.exists()) continue;
            try {
                android.os.StatFs stat = new android.os.StatFs(path.getPath());
                out.append(path.getAbsolutePath())
                        .append("  всего ").append(Fs.human(stat.getTotalBytes()))
                        .append("  свободно ").append(Fs.human(stat.getAvailableBytes()))
                        .append('\n');
            } catch (Exception ignored) {
            }
        }
        return out.length() == 0 ? "Хранилища не найдены" : out.toString().trim();
    }

    private File getExternalFilesDir() {
        return host.context().getExternalFilesDir(null);
    }

    private String systemInfo() {
        Runtime runtime = Runtime.getRuntime();
        android.os.StatFs stat = new android.os.StatFs(root.getPath());
        long uptime = android.os.SystemClock.elapsedRealtime() / 1000;
        return "Устройство: " + android.os.Build.MODEL
                + "\nПроизводитель: " + android.os.Build.MANUFACTURER
                + "\nAndroid: " + android.os.Build.VERSION.RELEASE
                + " (API " + android.os.Build.VERSION.SDK_INT + ")"
                + "\nАрхитектура: " + android.os.Build.SUPPORTED_ABIS[0]
                + "\nПроцессоров: " + runtime.availableProcessors()
                + "\nПамять JVM: " + Fs.human(runtime.maxMemory())
                + "\nХранилище: " + Fs.human(stat.getTotalBytes())
                + " (свободно " + Fs.human(stat.getAvailableBytes()) + ")"
                + "\nВремя работы: " + (uptime / 3600) + " ч " + (uptime % 3600) / 60 + " мин"
                + "\nПуть пользователя: " + root.getAbsolutePath();
    }

    private String color(List<String> args) throws IOException {
        String code = require(args, 0, "color <0a|0c|0f>");
        host.setColor(code);
        return "Цвет: " + code;
    }

    private String setEnv(List<String> args) {
        if (args.isEmpty()) {
            if (env.isEmpty()) return "Переменные не заданы";
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, String> entry : env.entrySet()) {
                out.append(entry.getKey()).append("=").append(entry.getValue()).append('\n');
            }
            return out.toString().trim();
        }
        String expression = String.join(" ", args);
        int equals = expression.indexOf('=');
        if (equals < 0) {
            String value = env.get(expression);
            return value == null ? "Переменная не найдена: " + expression : expression + "=" + value;
        }
        String key = expression.substring(0, equals).trim();
        String value = expression.substring(equals + 1).trim();
        if (value.isEmpty()) {
            env.remove(key);
            return "Переменная удалена: " + key;
        }
        env.put(key, value);
        return key + "=" + value;
    }

    private String sleep(List<String> args) throws IOException {
        String value = require(args, 0, "sleep <сек>");
        int seconds = Integer.parseInt(value);
        if (seconds < 0 || seconds > 60) throw new IOException("Укажите 0–60 секунд");
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Готово";
    }
}
