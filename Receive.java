package experiment_my;

import java.util.ArrayList;
import java.util.HashMap;

import jpcap.JpcapCaptor;
import jpcap.PacketReceiver;
import jpcap.packet.IPPacket;
import jpcap.packet.Packet;
import jpcap.packet.TCPPacket;
import jpcap.packet.UDPPacket;

class Receiver implements PacketReceiver {

	//inoder to break loop of captor, I have to put the class captor as member of this class
	static JpcapCaptor captor;

	static String lineSeparator = System.getProperty("line.separator");
	
	int intFlowAggregations = 6;  	//# of aggregation levels 
	public static int intTimeDuration = 0;		//capture duration	这个没用 之前版本才使用
	
	
	public  long longStartTime = 0;			//time of start
	public  long longNumberofPackets = 0;   	//# of processed packets 记录处理的总的packet数目
	public  long longOtherPackets = 0;		//# of packets that are not IPPackets 记录不是IP packet的数目，在异常检测中并没有用到，只是统计一下有多少不是ip包，结果看到很少很少 几乎没有不是ip包的包
	
	
	
	
	// bundle's packet number
	 int volumOfbundle;

	/*****************************
	*6 flow aggregations
	*0  <src_ip,dst_ip,protocol,src_port,dst_port>
	*1  src_ip
	*2  dst_ip
	*3  <src_ip,dst_ip>
	*4  src_port
	*5  dst_port
	*/
	String[] flowAggregation = new String[intFlowAggregations];
	//这个数组用来记录每个包在不同的aggregation level下的key
	//比如有个包 源ip： 1.1.1.11 目的IP 2.2.2.11 协议号 6 源端口 22 目的端口 21 
	//那么上面的[0]到【5】依次是 
	//"1.1.1.11,2.2.2.11,6,22,21"
	//"1.1.1.11"
	//"2.2.2.11"
	//"1.1.1.11,2.2.2.11"
	//"22"
	//"21"
	
	//这里有几个要注意的地方
	//直接调用getsourceaddress 在地址前面会多出个斜杠
	
	//另外，对于 ICMP IGMP等包， 他们没有 端口号这个属性
	//这里的处理是[0][4][5]都置为“NULL”
	//在后面的处理时，如果为“NULL”就忽略该包，不加入任何flow
	

/*
	public String flowAggregationFiveTuple = "";//<src_ip,dst_ip,protocol,src_port,dst_port>
	public String flowAggregationSourceIP = "";// src_ip
	public String flowAggregationDestinationIP = ""; //dst_ip
	public String flowAggregationHostPairs = ""; //<src_ip,dst_ip>
	public String flowAggregationSoucePort = ""; //src_port
	public String flowAggregationDestinationPort = ""; //dst_port
*/
	//there are intFlowAggregations(default: 6) different flow aggregations
	//for each aggregation, use a HashMap<String, List<Packet>> to store the flows
	//in the HashMap, key: one of the 6 flow aggregations; value: a list contains all the packets in the flow
	//there will be intFlowAggregations(default: 6) different HashMaps, put them in the collection of ArrayList
	//新建arraylist 一直有说道 后面的list<string>可以改成整型
	//public ArrayList< HashMap< String , List<String> > > flowMapArray = new ArrayList<HashMap<String , List<String>>>();
	//public ArrayList< ArrayList< HashMap< String , Long > > > flowMapBundleArray = new ArrayList< ArrayList<HashMap<String , Long> > >();
	public ArrayList< HashMap< String , Long > > flowMapArray;
	//这个是之前用来在内存中存储packet的，可以不考虑，不管他
	public HashMap<String, Packet> packetHashMap = new HashMap<String, Packet>();
	//true, store the packets in packetHashMap, 
	//false, no store, just get the number of packets in flows
	public  boolean storePacketFlag = false; //同样不管

	public void setstorePacketFlag(boolean flag){//同样不管
		storePacketFlag = flag;
	}

	public static void setReceiverStatics( int duration, String fileName) {
		intTimeDuration = duration;//同样不管
		try{
			captor = JpcapCaptor.openFile(fileName);//打开文件，包的来源文件
		}
		catch(Exception e){
  			e.printStackTrace();
		}
	/*	
		System.out.println("there are " + flowMapArray.size() + " aggregations");
		System.out.println("Receiver.localHostIPAddress: " + localHostIPAddress);
		System.out.println("Receiver.localHostMAC: " + localHostMAC);
		try{
			Scanner s = new Scanner(System.in);
			int intin = s.nextInt();
		}
		catch(Exception e){
  			e.printStackTrace();
		}
	*/
		return;
	}

	
	public Receiver(boolean flag, int num){
		longNumberofPackets = 0;   	//# of processed packets
		longOtherPackets = 0;		//# of packets that are not IPPackets		
		//System.out.println("call Receiver()...");
		storePacketFlag = flag; //这句可以不管，参数flag可以不管
		//Packet packet;			//for building initial bin
		volumOfbundle = num;
		
		/*for ( int j = 0; j < intFlowAggregations; j++)
			flowMapArray.add(new HashMap< String, Long>());*/
		
		
		//initialize the bin
		/*for( int i = 0; i < intBundleNum; i++) {
			flowMapArray = new ArrayList<HashMap<String, Long>>();
			for( int j = 0; j < packetNumOfBundle; j++) 
			{
				if(setFlowaggregation(captor.getPacket()) == 0)
					updateFlows();		
			}
			flowMapBundleArray.add(flowMapArray);
			
		}*/
		
	}

	public Receiver() {
		//default number for test
		volumOfbundle = 30000;
	}

	public Receiver(int num, int flowNum) {
		volumOfbundle = num;
		intFlowAggregations = flowNum;
	}
	
	/*public Receiver(int packetsOfBundle, int intFlowAggregation) {
		// TODO Auto-generated constructor stub
	}*/

	public ArrayList<HashMap<String, Long>> getBundle(boolean model, int timeout) {
		captor.setNonBlockingMode(model);
		captor.setPacketReadTimeout(timeout);
		flowMapArray = new ArrayList<HashMap<String, Long>>();
		for ( int j = 0; j < intFlowAggregations; j++)
			flowMapArray.add(new HashMap< String, Long>());
		captor.processPacket(volumOfbundle, this);
		return flowMapArray;
		
	}
	//extract the flow aggregation from a packet
	//if the packet is IP packet, return 0
	//otherwise, return -1
	 int setFlowaggregation( Packet packet){//这里就是对packet进行不同level的key的提取
	//很简单就是找出5元组的信息，如果不存在的属性就设置为“NULL” 前面也有讲到

		//System.out.println(packet.getClass());

		String sourceIP = "";
		String destinationIP = "";
		String protoclNumber = "";
		String sourcePort = "";
		String destinationPort = "";

		flowAggregation[0] = "";//5-tuple
		flowAggregation[1] = "";//source IP
		flowAggregation[2] = "";//destination IP
		flowAggregation[3] = "";//<src_IP, dst_IP>
		flowAggregation[4] = "";//sourcePort
		flowAggregation[5] = "";//destinationPort

		//only process the IP packets
		//use longOtherPackets to record the number of packets that are not IP packets
		if ( packet instanceof IPPacket ){
			IPPacket ipPacket = (IPPacket)packet;
			sourceIP += ipPacket.src_ip; // source IP
			destinationIP += ipPacket.dst_ip; // destination IP
			protoclNumber += ipPacket.protocol; //protocol;

			if ( packet instanceof UDPPacket ){
				UDPPacket udpPacket = (UDPPacket)packet;
				sourcePort += udpPacket.src_port; // source port
				destinationPort += udpPacket.dst_port; // destination port
			}//end of if (UDPPacket if )
			else {
				if ( packet instanceof TCPPacket) {
					TCPPacket tcpPacket = (TCPPacket)packet;
					sourcePort += tcpPacket.src_port;//source port
					destinationPort += tcpPacket.dst_port; //destination port
				}//end of if (TCPPacket if)
				else{
					sourcePort += "NULL";//source port
					destinationPort += "NULL";//destination port
				}//end of else (TCPPacket else)
			}//end of else (UDPPaket else)
		}// end of IPPacket if
		else {  //ignore the non-IP packets
				longOtherPackets ++;

				flowAggregation[0] += "NULL";
				flowAggregation[1] += "NULL";
				flowAggregation[2] += "NULL";
				flowAggregation[3] += "NULL";
				flowAggregation[4] += "NULL";
				flowAggregation[5] += "NULL";
				return -1;
		}// end of else (IPPacket else)

		flowAggregation[1] += sourceIP;//source IP
		flowAggregation[2] += destinationIP;//destination IP

		flowAggregation[3] += sourceIP;//<src_IP, dst_IP>
		flowAggregation[3] += ",";//<src_IP, dst_IP>
		flowAggregation[3] += destinationIP;//<src_IP, dst_IP>

		//if sourcePort or destination is NULL
		//ignore the packet in flowAggregaion[0],[4],[5] level
		if ( sourcePort.equals("NULL") ){
			flowAggregation[0] += "NULL";
			flowAggregation[4] += "NULL";
			flowAggregation[5] += "NULL";
		}// end of if (ignore [0,4,5] level aggregation)
		else{
			//5-tuple
			flowAggregation[0] += sourceIP;
			flowAggregation[0] += ",";
			flowAggregation[0] += destinationIP;
			flowAggregation[0] += ",";
			flowAggregation[0] += protoclNumber;
			flowAggregation[0] += ",";
			flowAggregation[0] += sourcePort;
			flowAggregation[0] += ",";
			flowAggregation[0] += destinationPort;
			//source port
			flowAggregation[4] += sourcePort;
			//destination port
			flowAggregation[5] += destinationPort;
		}// end of else (ignore [0,4,5] level aggregation)

		return 0;
	}//end of func setFlowaggragation

	public void setStarttime( long time){
		longStartTime = time;
		return;
	}

	long lastTime = 0;



	public void receivePacket( Packet packet){//每收到一个包，就会调用一次这个函数

	/*	
		//break loop when exceeding time duratin
		long currenTime = System.currentTimeMillis();
		if ( currenTime - longStartTime > intTimeDuration){
			//System.out.println("\nStart   time : " + longStartTime + "\nCurrent time : " + currenTime + "\nYou shoud break loop here");
			captor.breakLoop();
		}
	*/
		//System.out.println("bugging call receivePacket...");

		//capture one packet
		longNumberofPackets += 1;
		


//这个if是我查看内存使用信息的，可以不管
		/*if ( longNumberofPackets < 100000000 ){ //less than 10000000, print informantion every 1000000 packets
			if ( longNumberofPackets % 2000000 == 0){
				if ( lastTime == 0){
					lastTime = System.currentTimeMillis();
					System.out.println("duration from last print: " + (lastTime - longStartTime));
				}
				else {
					System.out.println("duration from last print: " + (System.currentTimeMillis()-lastTime));
					lastTime = System.currentTimeMillis();
				}
				System.out.println("captured " + longNumberofPackets + " packets");
				System.out.println("packetHashMap.size() is " + packetHashMap.size());
			
			//System.out.println("Max   memory is : " + (Runtime.getRuntime().maxMemory()/1024) + "\tMB");
            //System.out.println("Total memory is ：" + (Runtime.getRuntime().totalMemory()/1024) + "\tMB"); 
            //System.out.println("Free  memory is ：" + (Runtime.getRuntime().freeMemory()/1024) + "\tMB");
  		
  				System.out.println("Max   memory is : " + Runtime.getRuntime().maxMemory() );
            	System.out.println("Total memory is : " + Runtime.getRuntime().totalMemory() ); 
            	System.out.println("Free  memory is : " + Runtime.getRuntime().freeMemory() ); 
            	System.out.println("Used  memoty is : " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()));
			}
		}
	/*	else{//exceed 10000000, print informantion more often, every 1000 pacekts
			if ( longNumberofPackets % 10000== 0){
				System.out.println("************************************************" );
				System.out.println("captured " + longNumberofPackets + " packets");
				System.out.println("packetHashMap.size() is " + packetHashMap.size());
			
			//System.out.println("Max   memory is : " + (Runtime.getRuntime().maxMemory()/1024) + "\tMB");
            //System.out.println("Total memory is ：" + (Runtime.getRuntime().totalMemory()/1024) + "\tMB"); 
            //System.out.println("Free  memory is ：" + (Runtime.getRuntime().freeMemory()/1024) + "\tMB");
  				System.out.println("duration from last print: " + (System.currentTimeMillis()-lastTime));
				lastTime = System.currentTimeMillis();

  				System.out.println("Max   memory is : " + Runtime.getRuntime().maxMemory() );
            	System.out.println("Total memory is : " + Runtime.getRuntime().totalMemory() ); 
            	System.out.println("Free  memory is : " + Runtime.getRuntime().freeMemory() ); 
            	System.out.println("Used  memoty is : " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()));
			}
		}
	*/	



		//parse the captured packet
		//extract the protocol number, src_ip/port, des_ip/port
	
		//if the packet is not IP packet, ignore it
		//this means that we filter the non-IP packets by default
		//this is a alternative solution, 
		//we also can let the non-IP packets pass by default, simply put the packet in the packetHashMap without checking
		
		//return 0, if IP packet; 
		//otherwise, returns -1 对每个packet提取flow aggregation信息，如果不是IP包，不处理，直接返回
		/*if ( setFlowaggregation(packet) != 0 ){
			System.out.println("not IP packets, ignore...");
			return;
		}*/
		
		setFlowaggregation(packet);

		//store the packet into the HashMap<String,Packet>  packetHashMap
		//get the key: sec:usec 在内存里存包要用到的，不管他
		String packetKey = "";
		packetKey += packet.sec;
		packetKey += ":";
		packetKey += packet.usec;


		//System.out.println("bugging, get the packet.key, " + packetKey);
		//in this HashMao<String, Packet> packetHashMap, the keys are different to each other
		//so, when put the packet into the HashMap, we don' t need to check if containsKey(...)
		//the reason to use a HashMap instead of vector is that, when we find the abnormal flows,
		//we need the keys to find and delete the packes 
		//if (packetHashMap.containsKey(packetKey))
			
		//在内存里存包要用到的，不管他
		if ( storePacketFlag == true){
			packetHashMap.put(packetKey, packet);
			//System.out.println(packetHashMap.size());
		}
		
		// add the flow aggregation
		//put the packet into different flows under different aggregation
		for ( int i = 0; i < intFlowAggregations; i ++){
			if ( !(flowAggregation[i].equals("NULL")) ){ // only process the not NULL feature, is it a wise thing to do?
				Long l = 1L;
				if ( flowMapArray.get(i).containsKey( flowAggregation[i] ) )
					l = flowMapArray.get(i).get(flowAggregation[i]) + 1;
				flowMapArray.get(i).put(flowAggregation[i], l);
			} // end != "NULL" if		
		}//end of for
		


/*     
//don't delete these codes
		//write the packet to txt file
		try {
			FileWriter fileWriter = new FileWriter("packet.txt",true);
			fileWriter.write(flowAggregation[0] + "\n" + packet + lineSeparator);
			fileWriter.close();
		}
		catch(Exception e){
  			e.printStackTrace();
 		}		
*/
		return;
	} // end func receivePacket
	
	
	//setFlowaggregation（）函数把每个packet的aggregation信息记录在数组里
	//这里在对应的aggregation level中，把该包加入到list<string>中
	//可以改成直接把size加1
	//put the packet into different flows under different aggregation
	
}// end class Receiver



