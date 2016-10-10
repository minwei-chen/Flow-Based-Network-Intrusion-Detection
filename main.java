package experiment_my;

import java.util.ArrayList;
import java.util.HashMap;


public class main {
	
	
	// argument ...
	static int packetsOfBin = 30000;
	static int packetsOfBundle = 5000;
	static int intFlowAggregation = 6;
	
	
	static ArrayList<HashMap<String, Long>> bundle;
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		Receiver.setReceiverStatics(0, "");
		Receiver rc = new Receiver(packetsOfBundle, intFlowAggregation);
		IntrusionDetection  detection = new IntrusionDetection(rc, packetsOfBin/packetsOfBundle, intFlowAggregation);
		while (true) {
			bundle = rc.getBundle(true, 100);
			detection.exeDetection(bundle);
		}
	}

}
