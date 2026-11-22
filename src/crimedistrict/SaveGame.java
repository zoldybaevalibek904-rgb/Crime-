package crimedistrict;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreNotFoundException;

final class SaveGame {
    private static final String NAME = "CrimeDistrict-city";
    private byte[] initial;

    SaveGame() {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(NAME,false);
            initial = store.getRecord(1);
        } catch (RecordStoreNotFoundException empty) {
            // A first launch has no checkpoint.
        } catch (Exception unavailable) {
            // The title can still start a fresh game.
        } finally {
            close(store);
        }
    }

    boolean available() { return initial != null; }
    boolean load(District city) { return Snapshot.decode(city,initial); }

    boolean write(District city) {
        RecordStore store = null;
        try {
            byte[] data = Snapshot.encode(city);
            store = RecordStore.openRecordStore(NAME,true);
            if (store.getNumRecords() == 0) store.addRecord(data,0,data.length);
            else store.setRecord(1,data,0,data.length);
            initial = data;
            return true;
        } catch (Exception unavailable) {
            return false;
        } finally {
            close(store);
        }
    }

    private static void close(RecordStore store) {
        if (store == null) return;
        try { store.closeRecordStore(); } catch (Exception unavailable) {}
    }
}
