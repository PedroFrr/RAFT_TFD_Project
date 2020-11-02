import java.rmi.RemoteException;

public class ClientImpl implements ClientLibrary {
    @Override
    public void execute(String committedEntry) throws RemoteException {
        System.out.println("Received ACK for: " + committedEntry + " from server");
    }
}
