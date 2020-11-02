import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

//Methods to be invoked over the network

public interface RaftLibrary extends Remote {
    Integer request(String request, int clientId) throws RemoteException; //method to handle the requests sent by the client
    Integer respondAppendEntry(int term,
                               int serverId,
                               int prevLogIndex,
                               int prevLogTerm,
                               Entry[] entries,
                               int leaderCommit) throws IOException; //Method that responds to AppendEntries
    Integer respondRequestVote(int term,
                                int candidateId,
                                int lastLogIndex,
                                int lastLogTerm) throws IOException;

    void InitializeServer(int serverId) throws IOException;
    void InitializeCli(int cliId) throws IOException;
}