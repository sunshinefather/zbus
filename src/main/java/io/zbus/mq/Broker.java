package io.zbus.mq;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.zbus.mq.Protocol.ServerInfo;
import io.zbus.mq.Protocol.TopicInfo;
import io.zbus.mq.Protocol.TrackerInfo;

/**
 * Broker represents dynamic MessageInvoker(commands to ZbusServer) to ZbusServer or ZbusServer list(HA), capable of
 * 1) dynamic selection of MessageInvoker for Producer and Consumer components
 * 2) configuration of ZbusServer selection
 * 3) add/remove ZbusServer
 * 4) monitor ZbusServer status change
 * 
 * @author rushmore (洪磊明) 
 */
public interface Broker extends Closeable {

	/**
	 * Select MessageInvoker for Producer, selection algorithm decided by ServerSelector.
	 * Caller has to call releaseInovker.
	 * 
	 * @param topic Topic on invocation
	 * @return 
	 * @throws IOException
	 */
	MessageInvoker selectForProducer(String topic) throws IOException;

	/**
	 * Select MessageInvoker for Consumer, selection algorithm decided by ServerSelector.
	 * Caller has to call releaseInovker.
	 * 
	 * @param topic Topic on invocation
	 * @return 
	 * @throws IOException
	 */
	MessageInvoker selectForConsumer(String topic) throws IOException;

	/**
	 * Release the MessageInvoker
	 * @param invoker
	 * @throws IOException
	 */
	void releaseInvoker(MessageInvoker invoker) throws IOException;

	/**
	 * Configure selection algorithm
	 * 
	 * @param selector
	 */
	void configServerSelector(ServerSelector selector);
	
	List<Broker> availableServerList();  

	void addServer(String serverAddress) throws IOException;

	void removeServer(String serverAddress) throws IOException;

	void addServerNotifyListener(ServerNotifyListener listener);

	void removeServerNotifyListener(ServerNotifyListener listener);
	
	String brokerAddress();

	/**
	 * Server selection algorithm for Producer and Consumer, decision made on RouteTable according
	 * to topic 
	 */
	public static interface ServerSelector { 
		String selectForProducer(RouteTable table, String topic); 
		String selectForConsumer(RouteTable table, String topic);
	}

	/**
	 * Server status change notification
	 */
	public static interface ServerNotifyListener { 
		void onServerJoin(Broker broker); 
		void onServerLeave(String serverAddress);
	}

	/**
	 *  Route table based on the Topics info across ZbusServers.
	 *  
	 *  ServerMap: server address => ServerInfo
	 *  TopicTable: topic string => list of server contains this topic(TopicInfo details)
	 *  VoteTable:  topic string => list of server voted this topic
	 *
	 */
	public static class RouteTable {  
		//Topic ==> ServerAddress list(voted servers)
		private Map<String, Set<String>> votesTable = new ConcurrentHashMap<String, Set<String>>(); 
		//Topic ==> Server List(topic span across ZbusServers)
		private Map<String, List<TopicInfo>> topicTable = new ConcurrentHashMap<String, List<TopicInfo>>(); 
		//Server Address ==> ServerInfo
		private Map<String, ServerInfo> serverMap = new ConcurrentHashMap<String, ServerInfo>();
		
		/**
		 * ServerAddress to ServerInfo
		 * @return
		 */
		public Map<String, ServerInfo> serverMap() {
			return serverMap;
		}

		/**
		 * Topic string to ServerList
		 * @return
		 */
		public Map<String, List<TopicInfo>> topicTable() {
			return topicTable;
		}

		public ServerInfo serverInfo(String serverAddress) {
			return serverMap.get(serverAddress);
		}

		public ServerInfo randomServerInfo() {
			if (serverMap.isEmpty())
				return null;

			List<ServerInfo> servers = new ArrayList<ServerInfo>(serverMap.values());
			return servers.get(new Random().nextInt(servers.size()));
		}
		
		public void removeServer(String serverAddress){
			Map<String, ServerInfo> serverMapLocal = new ConcurrentHashMap<String, ServerInfo>(serverMap);
			ServerInfo serverInfo = serverMapLocal.remove(serverAddress);
			if(serverInfo == null) return;
			
			Map<String, List<TopicInfo>> topicTableLocal = rebuildTable(serverMapLocal); 
			serverMap = serverMapLocal;
			topicTable = topicTableLocal;
		}

		public void update(ServerInfo serverInfo) { 
			Map<String, ServerInfo> serverMapLocal = new ConcurrentHashMap<String, ServerInfo>(serverMap);
			serverMapLocal.put(serverInfo.serverAddress, serverInfo);
			
			Map<String, List<TopicInfo>> topicTableLocal = rebuildTable(serverMapLocal); 
			serverMap = serverMapLocal;
			topicTable = topicTableLocal;
		} 
		
		private boolean canRemove(Set<String> votes){
			return votes.isEmpty();
		}
		
		public List<String> updateVotes(TrackerInfo trackerInfo){ 
			Map<String, Set<String>> votesTableLocal = new ConcurrentHashMap<String, Set<String>>(votesTable);
			
			List<String> toRemove = new ArrayList<String>();
			Iterator<Entry<String, Set<String>>> iter = votesTableLocal.entrySet().iterator();
			while(iter.hasNext()){
				Entry<String, Set<String>> e = iter.next();
				
				if(!trackerInfo.liveServerList.contains(e.getKey())){
					e.getValue().remove(trackerInfo.serverAddress);
				} 
				
				if(canRemove(e.getValue())){
					iter.remove();
					toRemove.add(e.getKey());
				}
			} 
			if(toRemove.isEmpty()) return toRemove;
			
			Map<String, ServerInfo> serverMapLocal = new ConcurrentHashMap<String, ServerInfo>(serverMap); 
			for(String server : toRemove){
				serverMapLocal.remove(server);
			}
			Map<String, List<TopicInfo>> topicTableLocal = rebuildTable(serverMapLocal); 
			votesTable = votesTableLocal;
			serverMap = serverMapLocal;
			topicTable = topicTableLocal;
			
			return toRemove; 
		}

		private Map<String, List<TopicInfo>> rebuildTable(Map<String, ServerInfo> serverMapLocal) {
			Map<String, List<TopicInfo>> table = new ConcurrentHashMap<String, List<TopicInfo>>();
			for (ServerInfo serverInfo : serverMapLocal.values()) {
				for (TopicInfo topicInfo : serverInfo.topicMap.values()) {
					List<TopicInfo> topicServerList = table.get(topicInfo.topicName);
					if (topicServerList == null) {
						topicServerList = new ArrayList<TopicInfo>();
						table.put(topicInfo.topicName, topicServerList);
					}
					topicServerList.add(topicInfo);
				}
			}
			return table;
		}
	}
}
