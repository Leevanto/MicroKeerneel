public class message {

    //kernel will deal with this
    private int senderPid;
    private int targetPid;
    private int what;
    private Object[] data;



    //here are my constructors for message
    public message (int targetPid, int what){
        this(targetPid, what, (Object) null, (Object) null);
    }

    public message (int targetPid, int what, Object data1){
        this(targetPid, what, data1, (Object) null);
    }

    public message(int targetPid, int what, Object data1, Object data2) {
        this.targetPid = targetPid;
        this.what = what;
        this.data = new Object[2];
        this.data[0] = data1;
        this.data[1] = data2;
    }

    protected int getSenderPid() {
        return senderPid;
    }

    protected void setSenderPid(int senderPid){
        this.senderPid = senderPid;
    }

    protected int getTargetPid(){
        return targetPid;
    }

    protected int getWhat() {
        return what;
    }

    protected Object[] getData() {
        return data;
    }

    protected Object getData(int index) {
        if (data == null || index < 0 || index >= data.length) {
            return null;
        }
        return data[index];
    }

}
