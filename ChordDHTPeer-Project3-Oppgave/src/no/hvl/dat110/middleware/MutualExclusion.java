/**
 * 
 */
package no.hvl.dat110.middleware;

import java.math.BigInteger;
import java.rmi.RemoteException;
import java.security.KeyStore.TrustedCertificateEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import no.hvl.dat110.rpc.interfaces.NodeInterface;
import no.hvl.dat110.util.LamportClock;
import no.hvl.dat110.util.Util;

/**
 * @author tdoy
 *
 */
public class MutualExclusion {
		
	/** lock variables */
	private boolean CS_BUSY = false;						// indicate to be in critical section (accessing a shared resource) 
	private boolean WANTS_TO_ENTER_CS = false;				// indicate to want to enter CS
	private List<Message> queueack; 						// queue for acknowledged messages
	private List<Message> mutexqueue;						// queue for storing process that are denied permission. We really don't need this for quorum-protocol
	
	private LamportClock clock;								// lamport clock
	private Node node;
	
	public MutualExclusion(Node node) throws RemoteException {
		this.node = node;
		
		clock = new LamportClock();
		queueack = new ArrayList<Message>();
		mutexqueue = new ArrayList<Message>();
	}
	
	public void acquireLock() {
		CS_BUSY = true;
	}
	
	public void releaseLocks() {
		WANTS_TO_ENTER_CS = false;
		CS_BUSY = false;
	}

	/*
	 * clear the queueack before requesting for votes
	 * clear the mutexqueue
	 * increment clock
	 * adjust the clock on the message, by calling the setClock on the message
	 * wants to access resource - set the appropriate lock variable
	 * start MutualExclusion algorithm		
	 * first, removeDuplicatePeersBeforeVoting. A peer can contain 2 replicas of a file. This peer will appear twice
	 * multicast the message to activenodes (hint: use multicastMessage)
	 * check that all replicas have replied (permission)
	 * if yes, acquireLock
	 * node.broadcastUpdatetoPeers
	 * clear the mutexqueue
	 * return permission
	 */
	
	public boolean doMutexRequest(Message message, byte[] updates) throws RemoteException {
		System.out.println(node.nodename + " wants to access CS");
		queueack.clear();
		mutexqueue.clear();
		clock.increment();
		message.setClock(clock.getClock());
		WANTS_TO_ENTER_CS = true;

		List<Message> list = removeDuplicatePeersBeforeVoting();
		multicastMessage(message, list);
		boolean permission = areAllMessagesReturned(list.size());
		
		if (areAllMessagesReturned(list.size())) {
			acquireLock();
			node.broadcastUpdatetoPeers(updates);
			mutexqueue.clear();
		}
		return permission ? true : false;
	}
	
	/*
	 * iterate over the activenodes
	 * obtain a stub for each node from the registry
	 * call onMutexRequestReceived()
	 */
	
	private void multicastMessage(Message message, List<Message> activenodes) throws RemoteException {
		for (Message node : activenodes) {
			 Util.getProcessStub(node.getNodeIP(), node.getPort()).onMutexRequestReceived(message);
		}
	}
	// increment the local clock
	// if message is from self, acknowledge, and call onMutexAcknowledgementReceived()
	// write if statement to transition to the correct caseid
	// caseid=0: Receiver is not accessing shared resource and does not want to (send OK to sender)
	// caseid=1: Receiver already has access to the resource (dont reply but queue the request)
	// caseid=2: Receiver wants to access resource but is yet to - compare own message clock to received message's clock
	// check for decision
	
	public void onMutexRequestReceived(Message message) throws RemoteException {
		int caseid = -1;
		clock.increment();
		
		if (message.getNodeID().equals(node.getNodeID())) {
			message.setAcknowledged(true);
			onMutexAcknowledgementReceived(message);
		}
					
		if (!CS_BUSY && !WANTS_TO_ENTER_CS) {
			caseid = 0;
		} else if (CS_BUSY) {
			caseid = 1;
		} else if (WANTS_TO_ENTER_CS) {
			caseid = 2;
		}
		
		doDecisionAlgorithm(message, mutexqueue, caseid);
	}
	
	public void doDecisionAlgorithm(Message message, List<Message> queue, int condition) throws RemoteException {
		String procName = message.getNodeIP();			// this is the same as nodeName in the Node class
		int port = message.getPort();					// port on which the registry for this stub is listening
		
		switch(condition) {
			// case 1: Receiver is not accessing shared resource and does not want to (send OK to sender) */
			case 0: {
				message.setAcknowledged(true);
				Util.getProcessStub(procName, port).onMutexAcknowledgementReceived(message);
				break;
			}
		
			// case 2: Receiver already has access to the resource (dont reply but queue the request) */
			case 1: {
				queue.add(message);
				break;
			}
			
			/*
			 *  case 3: Receiver wants to access resource but is yet to (compare own message clock to received message's clock
			 *  the message with lower timestamp wins) - send OK if received is lower. Queue message if received is higher
			 */
			case 2: {
				int messageClock = message.getClock();
				int myClock = clock.getClock();

				if (messageClock == myClock){
					checkTimestamp(message.getNodeID().compareTo((node.getNodeID())) < 0, procName, port, queue, message);
				} else if (messageClock < myClock) {
					checkTimestamp(messageClock < myClock, procName, port, queue, message);
				}
				break;
			}
			default: break;
		}
	}
	
	private void checkTimestamp(boolean isLower, String procName, int port, List<Message> queue, Message message) throws RemoteException {
		if (isLower) {
			message.setAcknowledged(true);
			Util.getProcessStub(procName, port).onMutexAcknowledgementReceived(message);
		} else {
			queue.add(message);
		}
	}
	
	public void onMutexAcknowledgementReceived(Message message) throws RemoteException {
		// add message to queueack
		queueack.add(message);	
	}
	
	/*
	 *  multicast release locks message to other processes including self
	 *  iterate over the activenodes
	 *  obtain a stub for each node from the registry
	 *  call releaseLocks()
	 */
	public void multicastReleaseLocks(Set<Message> activenodes) {
		for(Message activeNode : activenodes){
			try{
				Util.getProcessStub(activeNode.getNodeIP(), activeNode.getPort()).releaseLocks();
			} catch (RemoteException e){
				e.printStackTrace();
			}
		}
	}
	
	/*
	 *  check if the size of the queueack is same as the numvoters
	 *  clear the queueack
	 *  return true if yes and false if no
	 */
	private boolean areAllMessagesReturned(int numvoters) throws RemoteException {
		if (queueack.size() == numvoters) queueack.clear();
		return queueack.size() == numvoters ? true : false;
	}
	
	private List<Message> removeDuplicatePeersBeforeVoting() {
		
		List<Message> uniquepeer = new ArrayList<Message>();
		for(Message p : node.activenodesforfile) {
			boolean found = false;
			for(Message p1 : uniquepeer) {
				if(p.getNodeIP().equals(p1.getNodeIP())) {
					found = true;
					break;
				}
			}
			if(!found)
				uniquepeer.add(p);
		}		
		return uniquepeer;
	}
}
