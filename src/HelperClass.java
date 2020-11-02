import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class HelperClass {

    private static final String SETTINGS_FILE = "server.ini";
    private static final String IP_LIST_FILE = "ip_list.txt"; //config file with every IP and Port of the cluster servers
    private static final String CLI_IP_LIST_FILE = "cli_ip_list.txt"; //config file with every IP and Port of the clients
    private static List<String> fellow = new ArrayList<>(); //list of fellow ip address on the cluster (not including its own)
    private static List<String> servers = new ArrayList<>(); //list of all servers of the cluster
    private long numServers;

    // @param configLine from ip_list.txt. Eg. http://127.0.0.1 5000
    public static String getAddress(String configLine){
        return configLine.split(" ")[0];
    }

    // @param addressEg. http://127.0.0.1
    public static String getHost (String address){
        return address.split("//")[1]; //returns the host Eg."127.0.0.1"
    }

    public static int getPort (String configLine){
        return Integer.parseInt(configLine.split(" ")[1]);
    }

    public static String getProperty(String propName) throws IOException {
        Properties prop = new Properties();
        InputStream input = new FileInputStream(SETTINGS_FILE);
        prop.load(input);
        return prop.getProperty(propName, null);
    }
    
    public static List<String> getFellow(int currentServerIndex) {

        Path path = Paths.get(IP_LIST_FILE);
        Path absolutePath = path.toAbsolutePath();
        String ip_list_Path = absolutePath.toString();

        //read file into stream
        //appends each line of the file to a list
        try (Stream<String> lines = Files.lines(Paths.get(ip_list_Path))) {

            fellow = lines.collect(Collectors.toList());

        } catch (IOException e) {
            e.printStackTrace();
        }

        fellow.remove(currentServerIndex); //removes the current server from the fellow. Should only obtain the rest
        
        return fellow;

    }
    public static List<String> getServers() {

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


        return servers;

    }


    public static String getServerConfigLine(int serverId){
        List<String> clusterMembers = new ArrayList<>(); //list of fellow ip address on the cluster (not including its own)
        Path path = Paths.get(IP_LIST_FILE);
        Path absolutePath = path.toAbsolutePath();
        String ip_list_Path = absolutePath.toString();

        //read file into stream
        //appends each line of the file to a list
        try (Stream<String> lines = Files.lines(Paths.get(ip_list_Path))) {

            clusterMembers = lines.collect(Collectors.toList());

        } catch (IOException e) {
            e.printStackTrace();
        }

        return clusterMembers.get(serverId);

    }

    public static String getCliServerConfigLine(int cliId){
        List<String> clusterMembers = new ArrayList<>(); //list of fellow ip address on the cluster (not including its own)
        Path path = Paths.get(CLI_IP_LIST_FILE);
        Path absolutePath = path.toAbsolutePath();
        String ip_list_Path = absolutePath.toString();

        //read file into stream
        //appends each line of the file to a list
        try (Stream<String> lines = Files.lines(Paths.get(ip_list_Path))) {

            clusterMembers = lines.collect(Collectors.toList());

        } catch (IOException e) {
            e.printStackTrace();
        }

        return clusterMembers.get(cliId);
    }




    public static int getElectionTimeout() throws IOException {
        Random r = new Random();
        return r.nextInt((Integer.parseInt(getProperty("ELECTION_TIMEOUT_MAX"))- Integer.parseInt(getProperty("ELECTION_TIMEOUT_MIN")) ) + 1) + Integer.parseInt(getProperty("ELECTION_TIMEOUT_MIN"));
    }

    public long getNumServers(){


        Path path = Paths.get(IP_LIST_FILE);
        Path absolutePath = path.toAbsolutePath();
        String ip_list_Path = absolutePath.toString();

        //read file into stream
        //appends each line of the file to a list
        try (Stream<String> lines = Files.lines(Paths.get(ip_list_Path))) {

            numServers = lines.count();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return numServers;
    }

}
