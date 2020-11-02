import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Math.min;

public class RaftLibraryImpl extends Thread implements RaftLibrary {

    private static RaftLog raftLog = new RaftLog(); // log containing server's entries
    private String request;
    private UUID clientId;
    private static ServerState serverState;
    private static List<String> fellow; //list of fellow ip address on the cluster (not including its own)
    private static List<String> servers; //list of all servers on the cluster
    private ServerStatus serverStatus; //server status; [leader, candidate, follower]
    private Timer heartbeatTimer;
    private Timer electionTimer;
    private int[] nextIndex;
    private int[] matchIndex;
    private Lock lock = new ReentrantLock();


    public RaftLibraryImpl() throws RemoteException {
    }

    //Handle requests from clients
    @Override
    public Integer request(String request, int clientId) throws RemoteException {
        System.out.println("Received '" + request + "' from client.");
        ServerStatus currentServerStatus = serverState.getServerStatus();

        //Only sends AppendEntries if it receives request from client and it is Leader
        if (currentServerStatus != ServerStatus.LEADER) {return serverState.getLeaderId();}

        int currentTerm = serverState.getTerm();
        Entry entry = new Entry(request, currentTerm, clientId);
        //adds the tuple <request,clientId> to the local EntryLog

        raftLog.insert(entry); //adds current Entry to t

        return null; //success
    }

    /*
    Method that handles incoming AppendEntries
     */

    public static LinkedList<Entry> getEntries() {
        if (serverState.getServerStatus() == ServerStatus.LEADER)
            return raftLog.getEntries();
        return null;
    }


    @Override
    public Integer respondAppendEntry(int leaderTerm,
                                      int leaderId,
                                      int prevLogIndex,
                                      int prevLogTerm,
                                      Entry[] entries,
                                      int leaderCommit) throws IOException {

        int currentServerId =serverState.getServerId();
        int currentTerm = serverState.getTerm();
        ServerStatus currentServerStatus = serverState.getServerStatus();

        if (entries != null) {

            System.out.println("Append Entry " +entries +" received from Server #"+leaderId + " " + new java.util.Date());

        }else{
            System.out.println("Heartbeat received from Server #"+leaderId + " " + new java.util.Date());
        }


        //Receiver implementation - Validations

        //1.  Reply false if term < currentTerm (§5.1)
        //To check if the leader (the one who sent the AppendEntry) is obsolete. If it is, we (the node) rejects the request
        System.out.println("Server #" + currentServerId + " | Current term: " + currentTerm + " || " + "Leader Term: " + leaderTerm );
        if (leaderTerm < currentTerm ) {
            return -1;
        }

        //if term is stale (new leader for example or the server was down)-> reset votedFor, update currentTerm and set LeaderId
        if (leaderTerm > currentTerm) {
            serverState.setTerm(leaderTerm);
        }

        int currentLeaderId = serverState.getLeaderId();
        if (currentLeaderId != leaderId){
            serverState.setVotedFor(0); //resets votedFor
            serverState.setLeaderId(leaderId); //Updates LeaderId
        }

        //Since the first validation passed (leaderTerm < currentTerm) it means the leaderTerm is at least as big as current term
        //So, if this servers isn't a FOLLOWER, convert it back to FOLLOWER
        if(currentServerStatus != ServerStatus.FOLLOWER){
            serverState.setServerStatus(ServerStatus.FOLLOWER);
            System.out.println("Server #"+currentServerId+" converted to FOLLOWER.");
        }

        //Reset election Timeout
        resetElectionTimeout();

        //2.  Reply false if log doesn’t contain an entry at prevLogIndex whose term matches prevLogTerm (§5.3)
        Entry prevLogIndexEntry = raftLog.getEntry(prevLogIndex);
        //If it returns -1; it means the follower log was empty
        if (prevLogIndex != -1 && prevLogIndexEntry.term != prevLogTerm) {
            return -1;
        }

        //3.  If an existing entry conflicts with a new one (same index but different terms), delete the existing entry and all that follow it (§5.3)
        if (serverState.getServerStatus() == ServerStatus.FOLLOWER){
            int lastLogIndex = raftLog.getLastIndex();
            if (leaderCommit == lastLogIndex) { // prev log entry = new log entry
                if(((raftLog.getEntry(lastLogIndex)).term) != leaderTerm){
                    for (int i = lastLogIndex; i < raftLog.getSize(); i++) {
                        Entry entry = raftLog.getEntry(i);
                        raftLog.delete(entry);
                    }
                    //raftLog.delete(prevLogIndexEntry);//delete prevLogIndexEntry
                    System.out.println("3rd true");
                }
            }
            else{
                System.out.println("3rd false");
                return 0;// nao sei que return meter nao percebi bem a logica
            }
        }


        // 4.  Append any new entries not already in the log
        if (entries != null) { //If the entries are not empty -> heartbeat was sent
            for (Entry entry: entries){
                if(!raftLog.containsEntry(entry).get()){ //if serverLog doesn't contain this entry, append it to the log
                    raftLog.insert(entry);
                }
            }
        }


        //5.  If leaderCommit > commitIndex, set commitIndex =min(leaderCommit, index of last new entry)


        if(leaderCommit > serverState.getCommitIndex()){
            serverState.setCommitIndex(min(leaderCommit, raftLog.getLastIndex()));
            System.out.println("5th true");
        }

        //prints every item on the EntryLog

        System.out.println("-----------------LOG---------------");
        raftLog.getEntries().forEach(entry-> System.out.println(entry+"\n"));

        return serverState.getTerm();


    }

    private void Timestamp(long currentTimeMillis) {
    }

    public Integer respondRequestVote(int term,
                                      int candidateId,
                                      int lastLogIndex,
                                      int lastLogTerm) throws IOException {

        //If the server is a FOLLOWER; reset the election timeout
        //Resets election timer
        ServerStatus currentServerStatus = serverState.getServerStatus();
        //1.  Reply false if term < currentTerm (§5.1)

        int currentTerm = serverState.getTerm();
        int votedFor = serverState.getVotedFor();
        int currentLogIndex = raftLog.getLastIndex();

        if(term < currentTerm){
            return 0;
        }

        /*
            2.  votedFor is null or candidateId, and candidate’s log is atleast as up-to-date as receiver’s log,
             grant vote (§5.2, §5.4)
        */

        if((votedFor == 0 || votedFor == candidateId ) &&  (term >= currentTerm && lastLogIndex >= currentLogIndex)){
            //Only if the server grants a vote, it should reset the election timeout
            if (currentServerStatus == ServerStatus.FOLLOWER) {
                resetElectionTimeout();
            }
            serverState.setVotedFor(candidateId); //set votedFor
            return serverState.getTerm(); //grant vote
        }

        return -1;

    }

    public int SendAppendEntry(String follower,
                               int term,
                               int serverId,
                               int prevLogIndex,
                               int prevLogTerm,
                               Entry[] entries,
                               int leaderCommit) {

        //Sends an AppendEntry for every follower.

        String address = HelperClass.getAddress(follower);
        String host = HelperClass.getHost(address);
        Integer port = HelperClass.getPort(follower);

        try {

            Registry registry = LocateRegistry.getRegistry(host, port);
            RaftLibrary raftLibraryStub = (RaftLibrary) registry.lookup(address);
            int success = raftLibraryStub.respondAppendEntry(term, serverId,prevLogIndex,prevLogTerm,entries,leaderCommit);

            if (success != -1) { //fellow server responded
                System.out.println(follower + " responded with success"); //handle responses from fellow servers

            }

        } catch (RemoteException | NotBoundException e ) {
            System.out.print(follower + " Not responding\n"); //handle no communication from fellow servers
            return 1; //return 1 em caso de falha
        } catch (IOException e) {
            e.printStackTrace();
        }


        return 0;

    }



    public Timer ElectionTimer(int electionTimeout){

        Timer timer = new Timer();

        TimerTask task = new TimerTask() {

            @Override
            public void run() {
                try {
                    handleElectionTimeout();
                } catch (Exception e) {
                    System.err.println("Server exception: " + e.toString());
                    e.printStackTrace();
                }
            }
        };

        timer.schedule(task,electionTimeout); //Runs the task once

        return timer;
    }


    public void handleElectionTimeout() throws IOException {

        int currentServerId = serverState.getServerId();
        System.out.println("Server #"+currentServerId +" timed out. Beginning election");

        //Resets election
        resetElectionTimeout();


        AtomicInteger voteCounter = new AtomicInteger();

        ServerStatus currentServerStatus = serverState.getServerStatus();

        int numServers = fellow.size()+1; //num servers + this replica

        if (currentServerStatus == ServerStatus.FOLLOWER) {
            serverState.setServerStatus(ServerStatus.CANDIDATE);
            System.out.println("Server #"+currentServerId +" converted to CANDIDATE.");


        /*
        • On conversion to candidate, start election:
        • Increment currentTerm
        • Vote for self
        • Reset election timer
        • Send RequestVote RPCs to all other servers
        Quando se tornar leader, manda um AppendEntry RPC  para indicar aos restantes que um leader já foi elegido.
         */

        //Election start
        int currentTerm;
        //Incremet currentTerm
        currentTerm = serverState.getTerm();
        serverState.setTerm(currentTerm + 1);
        currentTerm = serverState.getTerm();

        //vote for self
        serverState.setVotedFor(currentServerId);
        voteCounter.getAndIncrement(); //voteForSelf -> increment voteCounter

        //Send requestVote RPC to every server

        int lastIndex = raftLog.getLastIndex() == null ? 0: raftLog.getLastIndex();
        int lastTerm = raftLog.getLastTerm() == null ? 0: raftLog.getLastTerm();

        int finalCurrentTerm = currentTerm;
        //Lista local para trabalhar sobre. Será retirado daqui os followers que responderam com sucesso
        List<String> tempFellow = fellow;

        tempFellow.parallelStream().forEach(follower ->{

            int votedForThisServer = 0;
            try {
                votedForThisServer = SendRequestVote(follower,
                                                        finalCurrentTerm,
                                                        currentServerId,
                                                        lastIndex,
                                                        lastTerm);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (votedForThisServer != -1) {
                 voteCounter.getAndIncrement(); //updates atomically
            }
        });

        //waiting for results. If half or less of servers are alive it will be deadlocked
        while (true){


            if(voteCounter.get() > fellow.size() / 2){ //If it has received votes from the majority of the servers

                serverState.setServerStatus(ServerStatus.LEADER); //becomes leader
                serverState.setLeaderId(currentServerId);
                nextIndex = new int[numServers + 1];
                matchIndex = new int[numServers + 1];
                int mylastIndex = raftLog.getLastIndex();

                for (int server = 0; server <= numServers; server++) {
                    nextIndex[server] = mylastIndex + 1;
                    matchIndex[server] = 0;
                }

                System.out.println("Server #"+currentServerId+" converted to LEADER");
                electionTimer.cancel(); //Cancel electionTimeout when becoming Leader
                //sends empty heartbeats, so other servers don't timeout/ end other server elections
                //heartbeat -> no entries
                heartbeatTimer = SendContinuousHeartbeats(); //Starts timer to send Heartbeats

                break;  //exit while loop
            }

        }
        }


    }


    public int SendRequestVote (String follower,
                                int term,
                                int candidateId,
                                int lastLogIndex,
                                int lastLogTerm) throws InterruptedException {

        String address = HelperClass.getAddress(follower);
        String host = HelperClass.getHost(address);
        Integer port = HelperClass.getPort(follower);
        int currentServerId = serverState.getServerId();
        Thread th = new Thread();
        th.start();
        try {
            Registry registry = LocateRegistry.getRegistry(host, port);
            RaftLibrary raftLibraryStub = (RaftLibrary) registry.lookup(address);
            System.out.println("Server #"+ currentServerId + " Sent Request Vote to " + address + " " + port + " Thread ID: " + th.getId());
            int success = raftLibraryStub.respondRequestVote(   term,
                    candidateId,
                    lastLogIndex,
                    lastLogTerm);

            if (success != -1) { //fellow server responded

                System.out.println(follower + " responded with success"); //handle responses from fellow servers
                return success;
                }

        } catch (RemoteException | NotBoundException e) {
            System.out.print(follower + " not responding\n"); //handle no communication from fellow servers
        } catch (IOException e) {
            e.printStackTrace();
        }th.join();
        //System.out.println("Server sent request vote"+ " ");
        return 0;
    }



    //Sends continuous heartbeats to server [Done upon leader election]
    public Timer SendContinuousHeartbeats () throws IOException {
        int heartbeatInterval = Integer.parseInt(HelperClass.getProperty("HEARTBEAT_INTERVAL"));
        Timer timer = new Timer();

        TimerTask task = new TimerTask() {

            @Override
            public void run() {
                try {
                    int currentServerId = serverState.getServerId();
                    int prevLogIndex = nextIndex[currentServerId] - 1;
                    Entry entry = raftLog.getEntry(prevLogIndex);
                    int prevLogTerm = entry != null ? entry.term : -1;
                    int leaderCommit = serverState.getLeaderCommit();
                    int currentTerm = serverState.getTerm();
                    int mylastIndex = raftLog.getLastIndex();

                    // number of new entries sent to follower = mylastIndex - nextIndex[serverID]
                    Entry[] entries = new Entry[mylastIndex - nextIndex[currentServerId] + 1];
                    int i = nextIndex[currentServerId];
                    int j = 0;
                    while (i <= mylastIndex) {
                        entries[j] = raftLog.getEntry(i);
                        i++;
                        j++;
                    }
        Thread th = new Thread();
        th.start();


        //Envia AppendEntry
                    System.out.println("Sent AppendEntry RPC |" + " " + "Current term: " +currentTerm + " | "  + "Current Server ID: " + currentServerId + " |" + "Prev Log Index: " + prevLogIndex + " "
 + "| Prev Log Term: " + prevLogTerm + " | Entry: " + entries + " | Leader Commit: " + leaderCommit + " | Thread ID: " +th.getId());
                    fellow.parallelStream().forEach(follower ->{
                        SendAppendEntry(follower,
                                currentTerm,
                                currentServerId,
                                prevLogIndex,
                                prevLogTerm,
                                entries,
                                leaderCommit);
                    }); th.join();

                    //After sending all the entries to the clients and them committing them, we commit the LEADER index
                    int currentCommittedIndex = serverState.getCommitIndex();
                    int lastIndex = raftLog.getLastIndex();
                    if(currentCommittedIndex != lastIndex){
                        serverState.setCommitIndex(raftLog.getLastIndex());
                        for (int counter = currentCommittedIndex; counter < lastIndex; counter++){
                            Entry commitedEntry = raftLog.getEntry(counter);
                            if (commitedEntry != null){
                                execute(commitedEntry.cliId, commitedEntry.action); //Sends the action to the client specified on the cliId
                            }

                        }
                    }

                } catch (Exception e) {
                    System.err.println("Server exception: " + e.toString());
                    e.printStackTrace();
                }
            }
        };

        timer.scheduleAtFixedRate(task,0,heartbeatInterval);
        return timer;

    }
    @Override
    public void InitializeServer(int serverId) throws IOException {
        //Starts election timer
        serverState = new ServerState();
        resetElectionTimeout(); //start electionTimeout
        serverState.setServerId(serverId);
        fellow = HelperClass.getFellow(serverId); //initialize list of fellow members of the cluster
        serverState.setServerStatus(ServerStatus.FOLLOWER); //Start as follower
        StartCheckLastAppliedTimer(); //continuously check if commitIndex > lastApplied
    }

    public void InitializeCli(int cliId) throws IOException {
        //Starts election timer
        serverState = new ServerState();
        resetElectionTimeout();
        serverState.setServerId(cliId);
        servers = HelperClass.getServers(); //initialize list of fellow members of the cluser
//        serverState.setServerStatus(ServerStatus.FOLLOWER); //Start as follower
    }

    public void resetElectionTimeout() throws IOException {
        //if it is null (is being initialized) don't cancel it
        if(electionTimer != null){
            electionTimer.cancel();
        }

        int electionTimeout =  HelperClass.getElectionTimeout();
        electionTimer = ElectionTimer(electionTimeout);
    }

    //Continuously check if  commitIndex > lastApplied
    public synchronized void StartCheckLastAppliedTimer () throws IOException {
        int checkInterval = Integer.parseInt(HelperClass.getProperty("CHECK_LAST_APPLIED"));
        Timer checkLastAppliedTimer = new Timer();
        lock.lock();

        try {
            TimerTask task = new TimerTask() {

                @Override
                public void run() {
                    try {
                        int currentCommitIndex = serverState.getCommitIndex();
                        int currentLasApplied = serverState.getLastApplied();

                        if(currentCommitIndex > currentLasApplied){
                            //Increment lastApplied
                            serverState.setLastApplied(currentLasApplied + 1);
                        }

                    } catch (Exception e) {
                        System.err.println("Server exception: " + e.toString());
                        e.printStackTrace();
                    }
                }
            };
            checkLastAppliedTimer.scheduleAtFixedRate(task,0,checkInterval);

        } finally {
            lock.unlock();
        }
    }

    //Send the result of the operation to the client
    public String execute(int clientId,
                          String action) throws RemoteException, NotBoundException {

        String clientAddress = HelperClass.getCliServerConfigLine(clientId);
        String address = HelperClass.getAddress(clientAddress);
        String host = HelperClass.getHost(address);
        Integer port = HelperClass.getPort(clientAddress);

        Registry registry = LocateRegistry.getRegistry(host, port);
        ClientLibrary clientLibraryStub = (ClientLibrary) registry.lookup(address);
        //Sends action (String) to the client
        clientLibraryStub.execute(action);

        System.out.println("Replied (" + action + ","+clientId+") to the client. \n");

        //prints every item on the EntryLog

        System.out.println("-----------------LOG---------------");
        raftLog.getEntries().forEach(entry-> System.out.println("Client Id:"+entry.cliId+"|Term:"+entry.term+"|Action:"+entry.action+"\n"));

        return request;
    }
}
