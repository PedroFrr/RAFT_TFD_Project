import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Timer;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Server extends RaftLibraryImpl{

    private static String my_ip = ""; //IP address of this server
    private static Integer my_port = 0; //Port of this server
    private static ServerState serverState = new ServerState();;
    private static Integer serverId;

    public Server() throws RemoteException { }


    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            System.out.println("usage: Server <ServerId>");
            System.exit(1);
        }

        int serverId = Integer.parseInt(args[0]); //initialize ServerId -- in practice its the line number of the file

        setAddressAndPort(serverId); //sets address and port of this server

        RaftLibrary raftLibrary = new RaftLibraryImpl(){};

        RaftLibrary stub = (RaftLibrary) UnicastRemoteObject.exportObject(raftLibrary, my_port);

        stub.InitializeServer(serverId); //Initialize serverId

        try {
            Registry registry = LocateRegistry.createRegistry(my_port);
            registry.bind(my_ip, stub); //define the IP (address) as the name for the server

        } catch (Exception e) {
            System.out.println("RaftLibraryStore: ERROR trying to start server!");
            System.exit(0);
        }

        /* Get server address */
        String address = null;
        try {
          //  System.setProperty("java.rmi.server.hostname", "192.168.1.68");
            address = System.getProperty("java.rmi.server.hostname");
            // is address null? if so then it is 127.0.0.1 (localhost), else it still is address
            address = address == null ? "127.0.0.1" : address;
            address = "127.0.0.1";
        } catch (Exception e) {
            System.out.println("Can't get inet address.");
            System.exit(0);
        }

        String myID = new String(address + ":" + my_port + ":RAFT STORE");
        System.out.println(myID);
    }

    /*
    Sets server host, ip and port.
    Initializes the list of fellow address of the cluster servers
     */
    private static void setAddressAndPort(Integer serverId) throws IOException {

        String configLine = HelperClass.getServerConfigLine(serverId); //gets the config of this server (line in the file)
        my_ip = HelperClass.getAddress(configLine); //returns the ip of this server
        my_port  = HelperClass.getPort(configLine); //returns the port of this server

    }

}
