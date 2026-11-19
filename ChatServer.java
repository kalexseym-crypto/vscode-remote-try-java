import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatServer {
    static String name = "Server";
    static InetAddress clientAddr = null;
    static int clientPort = -1;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Запуск: java ChatServer <порт>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        DatagramSocket udp = new DatagramSocket(port);
        Scanner in = new Scanner(System.in);

        System.out.println("Сервер запущен на порту " + port);
        System.out.println("Команды: @name Имя | @send файл | @quit | текст");

        // Поток приёма UDP-сообщений
        Thread receiver = new Thread(() -> {
            try {
                byte[] buf = new byte[1024];
                while (true) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    udp.receive(p);
                    clientAddr = p.getAddress();
                    clientPort = p.getPort();
                    String msg = new String(p.getData(), 0, p.getLength());

                    if (msg.startsWith("@file ")) {
                        // "@file имя размер порт" — пришёл сигнал о передаче файла
                        String[] parts = msg.split(" ");
                        receiveFile(parts[1], Long.parseLong(parts[2]),
                                    Integer.parseInt(parts[3]), clientAddr);
                    } else {
                        System.out.println(msg);
                    }
                }
            } catch (Exception e) { /* сокет закрыт */ }
        });
        receiver.setDaemon(true);
        receiver.start();

        while (true) {
            String line = in.nextLine();
            if (line.equals("@quit")) {
                udp.close();
                System.exit(0);
            } else if (line.startsWith("@name ")) {
                name = line.substring(6).trim();
                System.out.println("Ваше имя: " + name);
            } else if (line.startsWith("@send ")) {
                sendFile(udp, line.substring(6).trim(), port + 1);
            } else if (!line.isEmpty()) {
                byte[] buf = (name + ": " + line).getBytes();
                udp.send(new DatagramPacket(buf, buf.length, clientAddr, clientPort));
            }
        }
    }

    // Отправка файла: поднимаем TCP-сервер, ждём клиента, льём байты
    static void sendFile(DatagramSocket udp, String filename, int tcpPort) {
        File f = new File(filename);
        if (!f.exists() || clientAddr == null) {
            System.out.println("Файл не найден или собеседник ещё не писал.");
            return;
        }
        try {
            // сообщаем по UDP: имя, размер, TCP-порт
            String signal = "@file " + f.getName() + " " + f.length() + " " + tcpPort;
            byte[] buf = signal.getBytes();
            udp.send(new DatagramPacket(buf, buf.length, clientAddr, clientPort));

            ServerSocket ss = new ServerSocket(tcpPort);
            System.out.println("Передаю файл " + f.getName() + "...");
            Socket s = ss.accept(); // ждём подключения получателя
            try (FileInputStream fis = new FileInputStream(f);
                 OutputStream out = s.getOutputStream()) {
                byte[] data = new byte[8192];
                int n;
                while ((n = fis.read(data)) > 0) out.write(data, 0, n);
            }
            ss.close();
            System.out.println("Файл отправлен.");
        } catch (Exception e) {
            System.out.println("Ошибка передачи: " + e.getMessage());
        }
    }

    // Приём файла по TCP
    static void receiveFile(String filename, long size, int tcpPort, InetAddress from) {
        System.out.println("Получаю файл " + filename + " (" + size + " байт)...");
        try (Socket s = new Socket(from, tcpPort);
             InputStream in = s.getInputStream();
             FileOutputStream fos = new FileOutputStream("received_" + filename)) {
            byte[] data = new byte[8192];
            long total = 0;
            int n;
            while (total < size && (n = in.read(data)) > 0) {
                fos.write(data, 0, n);
                total += n;
            }
            System.out.println("Файл сохранён как received_" + filename);
        } catch (Exception e) {
            System.out.println("Ошибка приёма: " + e.getMessage());
        }
    }
}