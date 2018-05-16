package local;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static Map<String, Connection> connectionMap=new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        ConsoleHelper.writeMessage("Введите порт сервера: ");
        int port=ConsoleHelper.readInt();
        ServerSocket serverSocket=new ServerSocket(port);
        ConsoleHelper.writeMessage("Сервер запущен");
        Socket socket=null;
        try {
            while (true) {
                socket = serverSocket.accept();
                Handler handler = new Handler(socket);
                handler.start();
            }
        }catch (IOException e){
            System.out.println(e.getMessage());
            serverSocket.close();
        }

    }
    public static void sendBroadcastMessage(Message message){
        for(Map.Entry<String, Connection> pair: connectionMap.entrySet())
        {
            try {
                pair.getValue().send(message);
            }catch (IOException e){
                System.out.println(e.getMessage());
            }
        }
    }

    private static class Handler extends Thread
    {
        private Socket socket;
        public Handler(Socket socket){
            this.socket=socket;
        }

        private String serverHandshake(Connection connection) throws IOException, ClassNotFoundException
        {
            while (true) {
                connection.send(new Message(MessageType.NAME_REQUEST));
                Message receive=connection.receive();
                if (receive.getType().equals(MessageType.USER_NAME))
                {
                    if (!receive.getData().isEmpty())
                    {
                        if (!connectionMap.containsKey(receive.getData()))
                        {
                            connectionMap.put(receive.getData(), connection);
                            connection.send(new Message(MessageType.NAME_ACCEPTED));
                            return receive.getData();
                        }
                    }
                }
            }
        }

        private void sendListOfUsers(Connection connection, String userName) throws IOException
        {
            for(Map.Entry<String, Connection> pair: connectionMap.entrySet())
            {
                if(!pair.getKey().equals(userName))
                connection.send(new Message(MessageType.USER_ADDED,pair.getKey()));
            }
        }

        private void serverMainLoop(Connection connection, String userName) throws IOException, ClassNotFoundException
        {
            while (true) {
                Message message = connection.receive();
                if(message.getType()==MessageType.TEXT)
                {
                    String data=userName+": "+message.getData();
                    Message messageNew=new Message(MessageType.TEXT,data);
                    sendBroadcastMessage(messageNew);
                }else ConsoleHelper.writeMessage("Сообщ. не явл. TEXT");
            }

        }

        @Override
        public void run() {
            ConsoleHelper.writeMessage("Установлено соединение c "+socket.getRemoteSocketAddress());
            String userName=null;
            try (Connection connection=new Connection(socket)){
                userName= serverHandshake(connection);
                sendBroadcastMessage(new Message(MessageType.USER_ADDED,userName));
                sendListOfUsers(connection,userName);
                serverMainLoop(connection,userName);
                connectionMap.remove(userName);
                sendBroadcastMessage(new Message(MessageType.USER_REMOVED, userName));
                ConsoleHelper.writeMessage("Connection close");
            }catch (IOException | ClassNotFoundException e) {
                ConsoleHelper.writeMessage("Произошла ошибка при обмене данными");
            }

        }
    }
}
