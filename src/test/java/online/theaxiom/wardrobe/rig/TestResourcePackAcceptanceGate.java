package online.theaxiom.wardrobe.rig;
import java.util.*;
import static online.theaxiom.wardrobe.rig.ResourcePackAcceptanceGate.*;
public class TestResourcePackAcceptanceGate {
    private static int checks;
    static void yes(boolean val) {checks++;if(!val)throw new AssertionError("check "+checks);}
    public static void main(String[] args){
      UUID required=UUID.fromString("3b230dc1-7f97-39c8-ad2c-df83c753841d");
      UUID other=UUID.fromString("717b6fb2-c317-4ff6-b0aa-a30e29bf8748");
      UUID player=UUID.fromString("87017064-74d7-327b-831e-270e2f005d93");
      var gate=new ResourcePackAcceptanceGate(required);
      yes(!gate.mayRender(player));
      for(Status s:Status.values()) {
        gate.onStatus(player,other,s);
        yes(!gate.mayRender(player));
      }
      for(Status s:Status.values()) if(s!=Status.SUCCESSFULLY_LOADED){
        gate.onStatus(player,required,s);
        yes(!gate.mayRender(player));
      }
      gate.onStatus(player,required,Status.SUCCESSFULLY_LOADED);
      yes(gate.mayRender(player));
      gate.onStatus(player,other,Status.FAILED_RELOAD);
      yes(gate.mayRender(player)); // other pack doesn't revoke our success
      gate.onStatus(player,required,Status.FAILED_RELOAD);
      yes(!gate.mayRender(player));
      gate.onStatus(player,required,Status.SUCCESSFULLY_LOADED);
      gate.onQuit(player);
      yes(!gate.mayRender(player));yes(gate.acceptedCount()==0);
      System.out.println("AXIOM_WARDROBE_PACK_GATE_PASS checks="+checks);
    }
}
