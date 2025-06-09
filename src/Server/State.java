package Server;

import java.io.Serializable;

public class State implements Serializable {
    private String msg;
    private String id;

    public State(String id, String m) {
        this.id = id;
        this.msg = m;
    }

    public String getId() {
        return id;
    }
    public String getInfo(){
        return this.msg;
    }

    public void setInfo(String m){
        this.msg = m;
    }
    @Override
    public String toString() {
        return "State{" +
                "msg='" + msg + '\'' +
                ", id='" + id + '\'' +
                '}';
    }
}
