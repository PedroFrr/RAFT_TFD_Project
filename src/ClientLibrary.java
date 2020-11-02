import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientLibrary extends Remote  {
    void execute(String committedEntry)  throws RemoteException; //Sends a committed Entry to the client
}
