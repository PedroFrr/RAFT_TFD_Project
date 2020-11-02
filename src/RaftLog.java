import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

public class RaftLog {
    private LinkedList<Entry> mEntries = new LinkedList<Entry>();

    // @return entry at passed-in index, null if none
    public Entry getEntry (int index) {
        if ((index > -1) && (index < mEntries.size())) {
            return new Entry (mEntries.get (index));
        }

        return null;
    }

    // @return term of last entry in log
    public Integer getLastTerm () {
        if(!mEntries.isEmpty()){
            Entry entry = mEntries.getLast();
            if (entry != null) {
                return entry.term;
            }
        }
        return -1;
    }

    // @return index of last entry in log
    public Integer getLastIndex () {
        return (mEntries.size () - 1);
    }


    //Synchronized -> if a simultaneous thread is trying to access the resource, it waits until it's over
    public synchronized void insert (Entry entry) {
        if(entry != null){
            // Add the new entry
            mEntries.add(entry); //Adds all entry items to the mEntries
        } else{
            System.out.println("Empty entry");
        }
    }

    public void delete (Entry entry) {
        if(entry != null){
            mEntries.remove(entry);
        }
        else {
            System.out.println("Empty entry");
        }
    }

    public Integer getEntryTerm(Entry entry){
        if(mEntries.isEmpty()){ //If it's empty it means the log is empty (no replication yet)
            return -1;
        }
        return entry.term;
    }

    public Integer getSize(){
        return mEntries.size();
    }


    //devolve log mais Updated
    public int getUpdatedLog(RaftLog a, RaftLog b) //return 0 para igual; 1 para a more updt que b; 2 para b mais updt que a
    {
        int lastTermA = a.getLastTerm();
        int lastTermB = b.getLastTerm();
        int lastIndexA = a.getLastIndex();
        int lastIndexB = b.getLastIndex();

        if(lastIndexA==lastIndexB && lastTermA == lastTermB)
            return 0;

        if (lastIndexA==lastIndexB) {
            if (lastTermA < lastTermB)
                return 2;
            else
                return 1;
        }

        if(lastIndexA < lastIndexB)
            return 2;
        else
            return 1;
    }


    public AtomicBoolean containsEntry (Entry entry){
        AtomicBoolean containEntry = new AtomicBoolean(false);
        mEntries.forEach(item  ->  {
            if(entry.equals(item)){
                containEntry.set(true);
            }
        });
        return containEntry;
    }

    @Override
    public int hashCode(){
        return -1;
    }

    public void checkLog(int serverId){

        int lastTerm = getLastTerm();
        int lastIndex = getLastIndex();
        Entry lastEntry = getEntry(lastIndex);
        String lastAction = lastEntry.action;

        for (Entry entry : mEntries) {
            if(entry != lastEntry){
                entry.action = lastAction;
                entry.term = lastTerm;
            }
            while(!(entry == lastEntry && getEntryTerm(entry).equals(getEntryTerm(lastEntry)))){
                entry.term = lastTerm;
                entry.action = lastAction;
            }
        }
    }
    /*
    public void restoreLog(String ip, int port){

    }*/

   public void addEntries (LinkedList<Entry> entries){

       int j = 0;
       for (int i = 0; i < entries.size(); i++) {
           mEntries.add(getEntry(i)); //adds RaftLog entries to local entries
           j++;
       }
   }

    public LinkedList<Entry> getEntries (){

        return mEntries;
    }


}
