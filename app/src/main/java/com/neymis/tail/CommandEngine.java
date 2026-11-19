package com.neymis.tail;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.io.ByteArrayOutputStream;
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

        String pref(String key, String def);

        void setPref(String key, String value);

        void stream(List<String> lines);

        void setFontSize(int size);

        void showQr(android.graphics.Bitmap bitmap);

        void notify(String title, String text);

        void schedule(long delayMs, String command);
    }

    private final Host host;
    private final File root;
    private File cwd;
    private final List<String> history = new ArrayList<>();
    private final Map<String, String> env = new HashMap<>();
    private final Map<String, String> aliases = new HashMap<>();
    private String stdin = "";
    private boolean greenMode;

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
        HELP.put("alias", "alias [имя=команда] — псевдонимы команд");
        HELP.put("fetch", "fetch <url> [-o файл] — HTTP-запрос");
        HELP.put("notify", "notify <заголовок> <текст> — уведомление");
        HELP.put("at", "at <минуты> <команда> — отложенная задача");
        HELP.put("clip", "clip set|get|file <текст|файл> — буфер обмена");
        HELP.put("apk", "apk <пакет> — информация о приложении");
        HELP.put("base64", "base64 <файл> — Base64-кодирование");
        HELP.put("xxd", "xxd <файл> — hex-дамп");
        HELP.put("qr", "qr <текст> — показать QR-код");
        HELP.put("font", "font <размер> — размер шрифта консоли");
        HELP.put("dir/s", "dir/s — старый DOS-режим (пасхалка)");
    }

    CommandEngine(Host host, File root) {
        this.host = host;
        this.root = root;
        this.cwd = root;
        String saved = host.pref("aliases", "");
        for (String line : saved.split("\n")) {
            if (line.isEmpty()) continue;
            int equals = line.indexOf('=');
            if (equals > 0) aliases.put(line.substring(0, equals), line.substring(equals + 1));
        }
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

        if (trimmed.equals("!!")) {
            if (history.isEmpty()) return "История пуста";
            trimmed = history.get(history.size() - 1);
        } else if (trimmed.startsWith("!") && trimmed.length() > 1) {
            try {
                int number = Integer.parseInt(trimmed.substring(1));
                if (number < 1 || number > history.size()) return "Нет команды №" + number;
                trimmed = history.get(number - 1);
            } catch (NumberFormatException ignored) {
            }
        }
        history.add(trimmed);

        int appendAt = trimmed.indexOf(">>");
        int writeAt = trimmed.indexOf(">");
        boolean append = appendAt >= 0;
        int redirectAt = append ? appendAt : writeAt;
        if (redirectAt > 0) {
            String left = trimmed.substring(0, redirectAt).trim();
            String target = trimmed.substring(redirectAt + (append ? 2 : 1)).trim();
            String result = runPipeline(left);
            try {
                File file = resolve(target);
                if (append) {
                    java.io.FileOutputStream out = new java.io.FileOutputStream(file, true);
                    out.write(result.getBytes("UTF-8"));
                    out.close();
                } else {
                    Fs.writeAll(file, result.getBytes("UTF-8"));
                }
                return "Записано: " + file.getAbsolutePath();
            } catch (Exception e) {
                return "Ошибка записи: " + e.getMessage();
            }
        }
        return runPipeline(trimmed);
    }

    private String runPipeline(String line) {
        if (!line.contains("|")) return executeSingle(line);
        String output = "";
        for (String segment : line.split("\\|")) {
            stdin = output;
            output = executeSingle(segment.trim());
        }
        stdin = "";
        return output;
    }

    private String executeSingle(String line) {
        List<String> parts = tokenize(line);
        String command = parts.get(0).toLowerCase();

        if (command.equals("dir/s") || command.equals("dir", "/s")) {
            return dirsCascade();
        }

        if (aliases.containsKey(command)) {
            List<String> replacement = tokenize(aliases.get(command));
            List<String> merged = new ArrayList<>(replacement);
            merged.addAll(parts.subList(1, parts.size()));
            parts = merged;
            command = parts.get(0).toLowerCase();
        }

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
                case "alias" -> alias(args);
                case "fetch" -> fetch(args);
                case "notify" -> notify(args);
                case "at" -> schedule(args);
                case "clip" -> clip(args);
                case "apk" -> apkInfo(args);
                case "base64" -> base64(args);
                case "xxd" -> xxd(args);
                case "qr" -> qr(args);
                case "font" -> font(args);
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
        if (args.isEmpty()) return stdin;
        File file = resolve(args.get(0));
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
        if (args.isEmpty()) throw new IOException("Использование: grep <текст> [файл]");
        List<String> lines = args.size() > 1
                ? Fs.readLines(resolve(args.get(1)))
                : Arrays.asList(stdin.split("\n"));
        List<String> found = new ArrayList<>();
        String needle = args.get(0).toLowerCase();
        for (String line : lines) {
            if (line.toLowerCase().contains(needle)) found.add(line);
        }
        return found.isEmpty() ? "Совпадений нет" : String.join("\n", found);
    }

    private String head(List<String> args, boolean first) throws IOException {
        List<String> lines = args.isEmpty()
                ? Arrays.asList(stdin.split("\n"))
                : Fs.readLines(resolve(args.get(0)));
        int count = 10;
        if (args.size() > 1) count = Integer.parseInt(args.get(1));
        count = Math.min(count, lines.size());
        List<String> selected = first ? lines.subList(0, count)
                : lines.subList(lines.size() - count, lines.size());
        return String.join("\n", selected);
    }

    private String wc(List<String> args) throws IOException {
        if (args.isEmpty()) {
            return stdin.split("\n", -1).length + " строк · " + Fs.human(stdin.getBytes("UTF-8").length);
        }
        File file = resolve(args.get(0));
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
        List<String> output = new ArrayList<>();
        Map<String, Integer> labels = new HashMap<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.length() > 1 && line.endsWith(":")) {
                labels.put(line.substring(0, line.length() - 1), index);
            }
        }
        int index = 0;
        while (index < lines.size()) {
            String raw = lines.get(index).trim();
            String lower = raw.toLowerCase();
            if (raw.isEmpty() || raw.startsWith("#") || raw.endsWith(":")) {
                index++;
                continue;
            }
            if (lower.startsWith("goto ")) {
                Integer target = labels.get(raw.substring(5).trim());
                if (target == null) {
                    output.add("Нет метки: " + raw.substring(5).trim());
                    index++;
                } else {
                    index = target + 1;
                }
                continue;
            }
            if (lower.startsWith("if ")) {
                String[] parts = raw.split("\\s+", 4);
                if (parts.length >= 4) {
                    boolean negative = parts[1].equals("!exists");
                    boolean condition = resolve(parts[2]).exists() != negative;
                    if (condition) output.add("$ " + parts[3] + "\n" + run(parts[3]));
                }
                index++;
                continue;
            }
            if (lower.startsWith("for ")) {
                int doIndex = raw.indexOf(" do ");
                if (doIndex > 0) {
                    String[] head = raw.substring(4, doIndex).trim().split("\\s+");
                    String variable = head[0];
                    String command = raw.substring(doIndex + 4).trim();
                    for (int value = 1; value < head.length; value++) {
                        env.put(variable, head[value]);
                        output.add("$ " + command + "\n" + run(command));
                    }
                }
                index++;
                continue;
            }
            output.add("$ " + raw + "\n" + run(raw));
            index++;
        }
        return String.join("\n", output);
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
        String systemPing = systemPing(host);
        if (systemPing != null) return systemPing;
        return javaPing(host);
    }

    private String systemPing(String host) {
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"/system/bin/ping", "-c", "4", "-W", "2", host});
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            int code = process.waitFor();
            String text = output.toString().trim();
            if (!text.isEmpty() && (text.contains("bytes from") || text.contains("Ответ"))) {
                return text;
            }
            if (code == 0 && !text.isEmpty()) return text;
        } catch (Exception ignored) {
        }
        return null;
    }

    private String javaPing(String host) {
        StringBuilder out = new StringBuilder("Ping " + host + ":\n");
        try {
            java.net.InetAddress address = java.net.InetAddress.getByName(host);
            out.append("Адрес: ").append(address.getHostAddress()).append('\n');
            for (int i = 0; i < 4; i++) {
                long start = System.currentTimeMillis();
                boolean reachable;
                String note = "";
                try {
                    reachable = address.isReachable(1500);
                } catch (Exception e) {
                    reachable = false;
                    note = " (нет ответа)";
                }
                long ms = System.currentTimeMillis() - start;
                out.append(reachable ? "Ответ от " : "Таймаут: ")
                        .append(address.getHostAddress()).append(": ")
                        .append(ms).append(" мс").append(note).append('\n');
            }
            return out.toString().trim();
        } catch (Exception e) {
            return "Не удалось разрешить хост: " + host
                    + "\nПроверь интернет и написание адреса.";
        }
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
        String code = require(args, 0, "color <0a|0c|0f|2>");
        greenMode = code.equalsIgnoreCase("2") || code.equalsIgnoreCase("0a");
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

    private String alias(List<String> args) {
        if (args.isEmpty()) {
            if (aliases.isEmpty()) return "Псевдонимы не заданы";
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                lines.add(entry.getKey() + "=" + entry.getValue());
            }
            Collections.sort(lines);
            return String.join("\n", lines);
        }
        String expression = String.join(" ", args);
        int equals = expression.indexOf('=');
        if (equals < 0) {
            String value = aliases.get(expression);
            return value == null ? "Псевдонима нет: " + expression : expression + "=" + value;
        }
        String key = expression.substring(0, equals).trim();
        String value = expression.substring(equals + 1).trim();
        if (value.isEmpty()) {
            aliases.remove(key);
        } else {
            aliases.put(key, value);
        }
        StringBuilder saved = new StringBuilder();
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            saved.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        host.setPref("aliases", saved.toString());
        return aliases.containsKey(key) ? key + "=" + aliases.get(key) : "Удалено: " + key;
    }

    private String fetch(List<String> args) throws IOException {
        String url = require(args, 0, "fetch <url> [-o файл]");
        File outputFile = null;
        if (args.size() > 2 && args.get(1).equals("-o")) outputFile = resolve(args.get(2));
        java.net.HttpURLConnection connection =
                (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        java.io.InputStream stream = status < 400 ? connection.getInputStream()
                : connection.getErrorStream();
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        if (stream != null) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) > 0) data.write(buffer, 0, count);
            stream.close();
        }
        byte[] bytes = data.toByteArray();
        if (outputFile != null) {
            Fs.writeAll(outputFile, bytes);
            return "HTTP " + status + " · сохранено: " + outputFile.getAbsolutePath()
                    + " (" + Fs.human(bytes.length) + ")";
        }
        String text = new String(bytes, "UTF-8");
        return "HTTP " + status + "\n" + (text.length() > 8000 ? text.substring(0, 8000) : text);
    }

    private String notify(List<String> args) throws IOException {
        if (args.isEmpty()) throw new IOException("Использование: notify <заголовок> <текст>");
        String title = args.get(0);
        String text = args.size() > 1 ? String.join(" ", args.subList(1, args.size())) : "";
        host.notify(title, text);
        return "Уведомление отправлено";
    }

    private String schedule(List<String> args) throws IOException {
        if (args.size() < 2) throw new IOException("Использование: at <минуты> <команда>");
        int minutes;
        try {
            minutes = Integer.parseInt(args.get(0));
        } catch (NumberFormatException e) {
            throw new IOException("Минуты должны быть числом");
        }
        if (minutes < 1 || minutes > 1440) throw new IOException("Укажите 1–1440 минут");
        String command = String.join(" ", args.subList(1, args.size()));
        host.schedule(minutes * 60 * 1000L, command);
        return "Задача через " + minutes + " мин: " + command;
    }

    private String clip(List<String> args) throws IOException {
        if (args.isEmpty()) throw new IOException("Использование: clip set|get|file <значение>");
        String action = args.get(0).toLowerCase();
        android.content.ClipboardManager manager = (android.content.ClipboardManager)
                host.context().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) return "Буфер обмена недоступен";
        if (action.equals("set") || action.equals("file")) {
            if (args.size() < 2) throw new IOException("Нет значения");
            String value = action.equals("file")
                    ? new String(Fs.readAll(resolve(args.get(1))), "UTF-8")
                    : String.join(" ", args.subList(1, args.size()));
            manager.setPrimaryClip(android.content.ClipData.newPlainText("Neymis Tail", value));
            return "Скопировано (" + value.length() + " симв.)";
        }
        if (action.equals("get")) {
            android.content.ClipData clip = manager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "Буфер пуст";
            CharSequence text = clip.getItemAt(0).getText();
            return text == null ? "В буфере не текст" : text.toString();
        }
        return "Использование: clip set|get|file <значение>";
    }

    private String apkInfo(List<String> args) throws IOException {
        String packageName = require(args, 0, "apk <пакет>");
        try {
            android.content.pm.PackageInfo info = host.context().getPackageManager()
                    .getPackageInfo(packageName, android.content.pm.PackageManager.GET_PERMISSIONS);
            String label = host.context().getPackageManager()
                    .getApplicationLabel(info.applicationInfo).toString();
            StringBuilder out = new StringBuilder("Приложение: " + label
                    + "\nПакет: " + info.packageName
                    + "\nВерсия: " + info.versionName + " (" + info.versionCode + ")"
                    + "\nУстановлено: " + new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    .format(new Date(info.firstInstallTime))
                    + "\nПуть APK: " + info.applicationInfo.sourceDir);
            if (info.requestedPermissions != null) {
                out.append("\nРазрешения (").append(info.requestedPermissions.length).append("):\n");
                for (String permission : info.requestedPermissions) {
                    out.append("  ").append(permission).append('\n');
                }
            }
            return out.toString().trim();
        } catch (Exception e) {
            return "Приложение не найдено: " + packageName;
        }
    }

    private String base64(List<String> args) throws IOException {
        byte[] data = stdin.isEmpty()
                ? Fs.readAll(resolve(require(args, 0, "base64 <файл>")))
                : stdin.getBytes("UTF-8");
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
    }

    private String xxd(List<String> args) throws IOException {
        String source = args.isEmpty() ? stdin : new String(
                Fs.readAll(resolve(args.get(0))), "UTF-8");
        byte[] data = source.getBytes("UTF-8");
        int limit = Math.min(data.length, 512);
        StringBuilder out = new StringBuilder();
        for (int offset = 0; offset < limit; offset += 16) {
            out.append(String.format("%04x  ", offset));
            for (int i = 0; i < 16; i++) {
                out.append(offset + i < limit
                        ? String.format("%02x ", data[offset + i]) : "   ");
            }
            out.append(" ");
            for (int i = 0; i < 16 && offset + i < limit; i++) {
                byte b = data[offset + i];
                out.append(b >= 32 && b < 127 ? (char) b : '.');
            }
            out.append('\n');
        }
        return out.toString().trim() + (data.length > limit ? "\n… (" + data.length + " байт)" : "");
    }

    private String qr(List<String> args) throws IOException {
        String text = require(args, 0, "qr <текст>");
        try {
            com.google.zxing.qrcode.QRCodeWriter writer =
                    new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix matrix = writer.encode(text,
                    com.google.zxing.BarcodeFormat.QR_CODE, 512, 512);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    512, 512, android.graphics.Bitmap.Config.ARGB_8888);
            for (int x = 0; x < 512; x++) {
                for (int y = 0; y < 512; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            host.showQr(bitmap);
            return "QR-код для: " + text;
        } catch (Exception e) {
            return "Не удалось создать QR: " + e.getMessage();
        }
    }

    private String font(List<String> args) throws IOException {
        String value = require(args, 0, "font <размер>");
        int size = Integer.parseInt(value);
        if (size < 8 || size > 30) throw new IOException("Укажите размер 8–30");
        host.setFontSize(size);
        host.setPref("font_size", String.valueOf(size));
        return "Размер шрифта: " + size;
    }

    private String dirsCascade() {
        List<String> lines = new ArrayList<>();
        String symbols = "░▒▓█▓▒░◆◇○●▪▫";
        lines.add(" Volume in drive C has no label");
        lines.add(" Volume Serial Number is 2A00-56C0");
        lines.add("");
        lines.add(" Directory of " + root.getAbsolutePath());
        lines.add("");
        List<File> all = Fs.search(root, "", 1500);
        long totalBytes = 0;
        int fileCount = 0;
        SimpleDateFormat date = new SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault());
        for (File file : all) {
            if (!file.isFile()) continue;
            long size = file.length();
            totalBytes += size;
            fileCount++;
            String noise = symbols.charAt((int) (Math.abs(file.getName().hashCode()) % symbols.length()));
            lines.add(String.format("%-28s %12d  %s  %s",
                    file.getName().substring(0, Math.min(28, file.getName().length())),
                    size, date.format(new Date(file.lastModified())), noise));
        }
        lines.add("");
        lines.add("       " + fileCount + " File(s)  " + totalBytes + " bytes");
        android.os.StatFs stat = new android.os.StatFs(root.getPath());
        lines.add("       " + stat.getAvailableBytes() + " bytes free");
        lines.add("");
        lines.add(greenMode ? "C:\\>" : "Directory listing complete.");
        host.stream(lines);
        return "";
    }
}
