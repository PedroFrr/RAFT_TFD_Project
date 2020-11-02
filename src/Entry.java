import java.io.Serializable;
import java.util.Objects;

public class Entry implements Serializable {

    public String action;
    public int term;
    public int cliId;

    // @param entry's action
    // @param entry's term
    public Entry (String action, int term, int cliId) {
        this.action = action;
        this.term = term;
        this.cliId = cliId;
    }

    public Entry (Entry e) {
        action = e.action;
        term = e.term;
        cliId = e.cliId;
    }

    @Override
    public boolean equals(Object o) {

        if (o == this) return true;
        if (!(o instanceof Entry)) {
            return false;
        }
        Entry entry = (Entry) o;
        return Objects.equals(action, entry.action) &&
                Objects.equals(term, entry.term) &&
                Objects.equals(cliId, entry.cliId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, term, cliId);
    }

}

