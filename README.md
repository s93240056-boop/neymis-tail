# Neymis Tail

A command-line shell for Android. Not Termux, not `/system/bin/sh`: a small
console with phone-aware commands, written in Java, open source, offline.

## Commands

| Command | Description |
|---|---|
| `help [cmd]` | List commands or detailed help for one |
| `ls [path]`, `cd [path]`, `pwd` | Navigate the filesystem |
| `cat <file>` | Print a text file |
| `find <name>` | Search the storage |
| `grep <text> <file>` | Search inside a file |
| `head/tail <file> [n]`, `wc <file>` | Read parts of a file |
| `mkdir <name>`, `touch <name>` | Create folders and files |
| `cp <src> <dst>`, `mv <src> <dst>`, `rm <path>` | File operations (`rm` uses a trash folder) |
| `zip <path>`, `unzip <file>` | Archives |
| `hash <file>` | MD5 and SHA-256 |
| `stat <file>`, `tree [path]` | File details and directory tree |
| `share <file>`, `open <file>` | Share or open a file |
| `df`, `apps [filter]`, `date`, `whoami` | Device info |
| `history`, `clear`, `echo`, `theme` | Shell controls |

Additional CMD-style commands (what makes sense on Android):

| Command | Description |
|---|---|
| `dir`, `md`, `rd`, `del`, `ren`, `type`, `findstr`, `cls` | CMD aliases |
| `attrib <file>`, `where <name>` | Attributes and lookup |
| `ipconfig [/all]`, `ping <host>`, `nslookup <domain>` | Network info and checks |
| `netstat`, `arp`, `getmac`, `route` | Tables from `/proc/net` |
| `tasklist`, `start <package>` | Processes and launching apps |
| `vol`, `systeminfo`, `ver` | Device and storage info |
| `color <0a\|0c\|0f>`, `set [NAME=value]`, `sleep <sec>`, `exit` | Console control |

Features: command history with ↑/↓, Tab completion, coloured output, and a
saved script feature (`run <file>` executes one command per line).

Not implemented: Windows-only tools (sfc, dism, chkdsk, diskpart, sc, netsh,
shutdown) — they require Windows APIs or root.

## Build

JDK 17 + Android SDK 34. Push a `v*` tag to build the APK in GitHub Actions;
the APK is attached to a release.

## License

MIT. See [LICENSE](LICENSE).
