package experiment_my;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;


public class IntrusionDetection {
	
	//the outside list represent bundle, as the inside flow level
	LinkedList<ArrayList<HashMap<String, Long>>> bin1;	
	LinkedList<ArrayList<HashMap<String, Long>>> bin2;
	ArrayList<HashMap<String, Long>> volumeChange;
	
	int numOfBundle = 6;			//default Bundles' number in one bin
	int intFlowAggregations = 6;  	//# of aggregation levels
	double Kthereshlod = 6.0;
	
	boolean timeBinFlag;	//true->intrusion, false->normal
	boolean[] aggregationFlag;
	
	
	// use default constructor ...
	Receiver rc;
	
	public IntrusionDetection(Receiver r, int bundleNum, int flowNum) {
		
		rc = r;
		numOfBundle = bundleNum;
		intFlowAggregations = flowNum;
		aggregationFlag = new boolean[intFlowAggregations];
		// initialize ...
		initBin(bin1);
		initBin(bin2);
		
		timeBinFlag = false;
		for(int i = 0; i < intFlowAggregations; i++) {
			aggregationFlag[i] = false;
		}
		
		volumeChange = new ArrayList<>();
		for (int i = 0; i < intFlowAggregations; i++) {
			volumeChange.add(new HashMap<String, Long>());
		}
		
		// get bundles for bin1, bin2
		for (int i = 0; i < numOfBundle; i++) {
			bin1.add(rc.getBundle(true, 0));
		}
		
		for (int i = 0; i < numOfBundle; i++) {
			bin2.add(rc.getBundle(true, 0));
		}
		
		// caiculate the changes of differet flows
		Iterator<String> keyIterator;
		Long l = 0l;
		String key;
		// bin2
		// 从bin2中把所有流的流量都记录到change当中
		for(int i = 0; i < intFlowAggregations; i++) {
			for (int j = 0; j < numOfBundle; j++) {
			keyIterator = bin2.get(j).get(i).keySet().iterator();
				while (keyIterator.hasNext()) {
					key = keyIterator.next();
					l = bin2.get(j).get(i).get(key);
					if(volumeChange.get(i).containsKey(key))	// 如果change中原本有这个流，要做一次加法
						l = volumeChange.get(i).get(key) + l;
					volumeChange.get(i).put(key, l);
				}
			}
		}
		// bin1
		for(int i = 0; i < intFlowAggregations; i++) {
			for (int j = 0; j < numOfBundle; j++) {
			keyIterator = bin1.get(j).get(i).keySet().iterator();
				while (keyIterator.hasNext()) {
					key = keyIterator.next();
					l = 0 - bin1.get(j).get(i).get(key);
					if(volumeChange.get(i).containsKey(key)) {		// 只处理在bin2中出现过的流
						l = volumeChange.get(i).get(key) + l;
						volumeChange.get(i).put(key, l);
					}
				}
			}
		}
	}
	
	//initialize the bin
	void initBin(LinkedList<ArrayList<HashMap<String, Long>>> bin) {
		
		bin = new LinkedList<>();
		for(int i = 0; i < numOfBundle; i++) {
			bin.add(new ArrayList<HashMap<String, Long>>());
			for(int j = 0; j < intFlowAggregations; j++) {
				bin.get(i).add((new HashMap<String, Long>()));
			}
		}
	}
	
	// 从volumechange中得到数据进行计算
	void detect() {
		
		long volumeChangeSum;
		double standardDeviation;
		Iterator<Long> valueIterator;
		double mean;
		double sumforvariance;
		double variance;
		int flowSize;
		
		for(int i = 0; i < intFlowAggregations; i++) {
			
			volumeChangeSum = 0;
			standardDeviation = 0.0;
			flowSize = volumeChange.get(i).size();
			
			valueIterator = volumeChange.get(i).values().iterator();
			while (valueIterator.hasNext()) {
				volumeChangeSum += valueIterator.next();
			}
			
			mean = (double)((double)volumeChangeSum / (double) flowSize);
			
			sumforvariance = 0.0;
			variance = 0.0; // fang cha
			valueIterator = volumeChange.get(i).values().iterator();
			long l;
			while(valueIterator.hasNext()) {
				l = valueIterator.next();
				sumforvariance += ( (double)l - mean ) * ( (double)l - mean );
			}
			
			variance = sumforvariance / (double)(flowSize -1);

			standardDeviation = Math.sqrt(variance);
			
			if ( standardDeviation == 0.0){
				timeBinFlag = true;
				aggregationFlag[i] = true;
				continue;
			}
			
			double AVV = mean * Math.sqrt(flowSize) / standardDeviation;
			
			if ( AVV < 0.0 )
				AVV = - AVV;
			
			//超过临界值，异常， 设置异常标志
			if ( AVV > Kthereshlod){
				timeBinFlag = true;
				aggregationFlag[i] = true;
			}
			
		}
		
		dealDetectionResult();
		
	}
	
	void dealDetectionResult(){

		if ( timeBinFlag == false ){
			//System.out.println("\nNo alarm triggered.");
			return;
		}
		else{
			int alarms = 0;
			for ( int i = 0; i < aggregationFlag.length; i ++){
				if ( aggregationFlag[i] == true ){
					System.out.println("\nThere is an alarm triggered in aggregation " + i);
					alarms ++;
				}
			}
			System.out.println("\nThere are " + alarms + " alarms triggered");
			System.exit(0);
		}

	}
	
	// 收到一个新的bundle后，更新bin1，bin2以及volumechange。
	void updateFlowInfo(ArrayList<HashMap<String, Long>> bundle) {
		
		// poll the first bundle from bin1
		ArrayList<HashMap<String, Long>> b1 = bin1.pollFirst();
		Iterator<String> keyIterator;
		String key;
		Long l;
		for (int i = 0; i < intFlowAggregations; i++) {
			
			keyIterator = b1.get(i).keySet().iterator();
			while(keyIterator.hasNext()) {
				key = keyIterator.next();
				l = b1.get(i).get(key);
				if(volumeChange.get(i).containsKey(key)) {
					l += volumeChange.get(i).get(key);
					volumeChange.get(i).put(key, l);
				}
			}
		}
		
		// add the last bundle into bin2
		bin2.addLast(bundle);
		for (int i = 0; i < intFlowAggregations; i++) {
			
			keyIterator = bundle.get(i).keySet().iterator();
			while(keyIterator.hasNext()) {
				key = keyIterator.next();
				l = bundle.get(i).get(key);
				if(volumeChange.get(i).containsKey(key))
					l += volumeChange.get(i).get(key);
				// volumechage中没有该流的记录，说明原本的bin2中没有该流，但并不代表bin1中没有该流，这点要考虑
				else {
					for (int j = 0; j < bin1.size(); j++) {
						if(bin1.get(j).get(i).containsKey(key)) 
							l -= bin1.get(j).get(i).get(key);	
					}
				}
				volumeChange.get(i).put(key, l);
			}
		}
		
		// do poll the first bundle from bin2, and add to bin1 at the last position.
		ArrayList<HashMap<String, Long>> b2 = bin2.pollFirst();
		bin1.addLast(b2);
		boolean isContained = false;
		for (int i = 0; i < intFlowAggregations; i++) {
					
			keyIterator = b2.get(i).keySet().iterator();
			while(keyIterator.hasNext()) {
				key = keyIterator.next();
				isContained = false;
				// 获取一个flow，查看该flow在新的bin2中是否存在
				for(int j = 0; j < bin2.size() && !isContained; j++) {
					if(bin2.get(j).get(i).containsKey(key))
						isContained = true;
				}
				// 如果新的bin2中有这个流，change值减去流量*2；不存在，则直接从volumechange中抛弃该流
				if (isContained) {
					l = -2 * b2.get(i).get(key);
					l += volumeChange.get(i).get(key);
					volumeChange.get(i).put(key, l);
				}
				else {
					volumeChange.get(i).remove(key);
				}
						
			}
		}
				
		
		//volumeChange.trimToSize();
	}


	public void exeDetection(ArrayList<HashMap<String, Long>> bundle) {
		updateFlowInfo(bundle);
		detect();
	}
}