import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Client extends RaftLibraryImpl implements Runnable{

    private static final String IP_LIST_FILE = "ip_list.txt"; //config file with every IP and Port of the cluster servers
    private static final String CLI_IP_LIST_FILE = "cli_ip_list.txt"; //confog file with every client IP and port
    private static String server_ip = "";   //IP address of the server
    private static Integer server_port = 0; //Port of the server
    private static List<String> servers;    //Array of existing servers
    private static Integer serverId = 0;  //The client will first try to contact server with Id 0
    private static HelperClass helperClass = new HelperClass();
    private static boolean leaderReplied = false;
    private static int request = 0; //First request that the client sends
    private static String my_ip = ""; //IP address of this client
    private static Integer my_port = 0; //Port of this client
    private static int cliId;

    public Client() throws RemoteException {
    }

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            System.out.println("usage: Client <ServerId>");
            System.exit(1);
        }

        cliId = Integer.parseInt(args[0]); //initialize ServerId -- in practice its the line number of the file

        //Initializes client
        setCliAddressAndPort(cliId);

        ClientLibrary clientLibrary = new ClientImpl(){};
        ClientLibrary stub = (ClientLibrary) UnicastRemoteObject.exportObject(clientLibrary, my_port);

        try {
            Registry registry = LocateRegistry.createRegistry(my_port);
            registry.bind(my_ip, stub);

        } catch (Exception e) {
            System.out.println("ERROR trying to start Client!");
            System.exit(0);
        }
        //end of client initialization

        try {

            Timer timer = new Timer();

            final long seconds = (1000*5); //5 seconds - the interval the timer executes in
            //Sends continuous requests to the server
            TimerTask task = new TimerTask() {

                @Override
                public void run() {

                    try {
                        setAddressAndPort(serverId); //split file info into address and port to connect

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    String host = server_ip.split("//")[1]; //returns only the host Eg."127.0.0.1" - takes off "http://"

                    RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
                    String jvmName = bean.getName();
                    long pid = Long.parseLong(jvmName.split("@")[0]);
                    //Changes client request if it was successfully sent to the LEADER
                    if(leaderReplied){ //If client didn't connect sends same request to the LEADER
                        request = getSaltString();
                    }

                    Thread th = new Thread();
                    th.start();
                    try {

                        Registry registry = LocateRegistry.getRegistry(host, server_port); //connects to server
                        RaftLibrary raftLibraryStub = (RaftLibrary) registry.lookup(server_ip); //stub

                        System.out.println("server IP " + server_ip + "server port " + server_port);
                        System.out.println("Sending to Server #"+serverId+"-> Request: " + request + ", Client ID: " + cliId);
                        System.out.println("File CliID " + cliId +" Client IP: " + my_ip + " Port:"+ my_port);

                        Integer response = raftLibraryStub.request(String.valueOf(request),cliId);

                        if(response != null){
                            System.out.println("The contacted server was not the leader. Will retry with Server #"+ response);
                            serverId = response;
                        }else{
                            leaderReplied = true;
                        }

                    } catch (Exception e ) {
                        //Client timed Out...... The contacted server was down
                        System.out.println("Couldn't contact server #"+serverId+". Retrying ...");
                        //Randomly connect to another Server
                        leaderReplied = false;
                        Random r = new Random();
                        int oldServerId = serverId;
                        while(serverId == oldServerId){
                            serverId = r.nextInt(Math.toIntExact(helperClass.getNumServers()) -1 );
                        }
                    }
                }
            };

            timer.scheduleAtFixedRate(task,0,seconds); //sends requests to the client at fixed rates

        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }

    /*
    Method that returns random strings for client requests
     */

    private static int getSaltString() {
        request++;
        return request;
    }



    private static void setAddressAndPort(Integer serverId) throws IOException { //reads a line of the file and saves server ip and port
        Path path = Paths.get(IP_LIST_FILE);
        Path absolutePath = path.toAbsolutePath();
        String ip_list_Path = absolutePath.toString();

        //read file into stream
        //appends each line of the file to a list
        try (Stream<String> lines = Files.lines(Paths.get(ip_list_Path))) {

            servers = lines.collect(Collectors.toList());

        } catch (IOException e) {
            e.printStackTrace();
        }

        String serverConfigLine =  servers.get(serverId); //gets the config of this server (line in the file)
        server_ip = serverConfigLine.split(" ")[0]; //returns the ip of this server
        server_port  = Integer.parseInt(serverConfigLine.split(" ")[1]); //returns the port of this server
    }


    private static void setCliAddressAndPort(int cliId) throws IOException {

        String configLine = HelperClass.getCliServerConfigLine(cliId); //gets the config of this client (line in the file)
        my_ip = HelperClass.getAddress(configLine); //returns the ip of this client
        my_port  = HelperClass.getPort(configLine); //returns the port of this client
    }
}