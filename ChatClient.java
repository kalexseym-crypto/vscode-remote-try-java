import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    static String name = "Client";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Запуск: java ChatClient <адрес> <порт>");
            return;
        }
        InetAddress addr = InetAddress.getByName(args[0]);
        int port = Integer.parseInt(args[1]);
        DatagramSocket udp = new DatagramSocket();
        Scanner in = new Scanner(System.in);

        System.out.println("Клиент запущен. Сообщения пойдут на " + args[0] + ":" + port);
        System.out.println("Команды: @name Имя | @send файл | @quit | текст");

        Thread receiver = new Thread(() -> {
            try {
                byte[] buf = new byte[1024];
                while (true) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    udp.receive(p);
                    InetAddress from = p.getAddress();
                    String msg = new String(p.getData(), 0, p.getLength());

                    if (msg.startsWith("@file ")) {
                        String[] parts = msg.split(" ");
                        receiveFile(parts[1], Long.parseLong(parts[2]),
                                    Integer.parseInt(parts[3]), from);
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
                sendFile(udp, line.substring(6).trim(), addr, port);
            } else if (!line.isEmpty()) {
                byte[] buf = (name + ": " + line).getBytes();
                udp.send(new DatagramPacket(buf, buf.length, addr, port));
            }
        }
    }

    static void sendFile(DatagramSocket udp, String filename, InetAddress addr, int port) {
        File f = new File(filename);
        if (!f.exists()) {
            System.out.println("Файл не найден.");
            return;
        }
        try {
            int tcpPort = port + 1;
            String signal = "@file " + f.getName() + " " + f.length() + " " + tcpPort;
            byte[] buf = signal.getBytes();
            udp.send(new DatagramPacket(buf, buf.length, addr, port));

            ServerSocket ss = new ServerSocket(tcpPort);
            System.out.println("Передаю файл " + f.getName() + "...");
            Socket s = ss.accept();
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