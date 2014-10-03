// class ReliableDatagramSocket:
//
// Sample template for 0657.312b Assignment I
// Implements a reliable datagram service over UDP

import java.util.zip.*;
import java.util.*;
import java.io.*;
import java.net.*;

// We extend DodgyDatagramSocket, which in turn extends DatagramSocket
// This replacement is transparent to you in terms of methods you can
// call on the socket, except that of course you should be filling in
// this skeleton with code to implement a reliable transport method
// over the "dodgy" transport provided by DodgyDatagramSocket.

class ReliableDatagramSocket extends DodgyDatagramSocket
{
    class IncomingPacketCatcher extends Thread
    {
        ReliableDatagramSocket parent;
        
        public IncomingPacketCatcher(ReliableDatagramSocket newParent)
        {
            parent = newParent;
        }
        
        public void run()
        {
            while(stillSending)
            {
                DatagramPacket incomingPacket = new DatagramPacket(new byte[1024], 1024);
                
                boolean packetGood = true;

                //wait for a packet
                try
                {
                    passTimeoutSet(500);
                    passReceivedPacket(incomingPacket);
                    //DEBUG System.err.println("Packet received");
                }
                catch (Exception timeoutException)
                {
                    try
                    {
                        System.err.println("Timeout, resending packets");
                        resendOutgoingPackets();
                        packetGood = false;
                    }
                    catch (IOException e)
                    {
                        System.err.println("Could not resend packets, exiting");
                        System.exit(1);
                    }
                }

                if(packetGood)
                {
                    //remove header information
                    String packetContents = new String(incomingPacket.getData(), 0, incomingPacket.getLength());
                    int checksumEnd = packetContents.indexOf('&');
                    int headerEnd = packetContents.indexOf('*');

                    //if header was corrupt then dont do this code
                    if(checksumEnd >= 0 && headerEnd >= 0)
                    {
                        byte[] data = incomingPacket.getData();
                        for(int i = 0; i + headerEnd + 1 < incomingPacket.getLength(); i++)
                        {
                            data[i] = data[i + headerEnd + 1];
                        }

                        incomingPacket.setLength(incomingPacket.getLength() - (headerEnd + 1));

                        try
                        {
                            long packetChecksum = Long.parseLong(packetContents.substring(0, checksumEnd));
                            int packetSequenceNumber = Integer.parseInt(packetContents.substring(checksumEnd + 1, headerEnd));
                
                            CRC32 checksum = new CRC32();
                            checksum.update(incomingPacket.getData(), 0, incomingPacket.getLength());
                            
                            //check if packet is an ack or data
                            String dataAsString = new String(incomingPacket.getData(), 0, incomingPacket.getLength());
                            //if ack, then remove relevant packets from outgoing packet list
							//DEBUG System.err.println(dataAsString);
                            if (dataAsString.startsWith("acknowledged"))
                            {
                                //gets the sequence number written after the word acknowledged in the data string
                                int ackSequenceNumber = Integer.parseInt(dataAsString.substring(13));

								//DEBUG System.err.println("Removing all packets before " + ackSequenceNumber);
                                removeOutgoingPackets(ackSequenceNumber);
                            }
                            //if data, if valid add to incoming packet list
                            else if(packetSequenceNumber == sequenceNumberReceive && packetChecksum == checksum.getValue())
                            {
                                //DEBUG System.err.println("Adding packet " + sequenceNumberReceive + " to incoming list");
                                parent.incomingPackets.add(incomingPacket);

                                sequenceNumberReceive++;
                            }
                        }
                        catch (Exception e)
                        {
                            //packet header must have been corrupted, safe to do nothing as packet will not be added
                        }
                    }
                      
                    //send ack for latest packet received
                    String ack = new String("0&0*acknowledged " + (sequenceNumberReceive - 1));
                    DatagramPacket acknowledgement = new DatagramPacket(ack.getBytes(), ack.length(), incomingPacket.getAddress(), incomingPacket.getPort());                    
                    try
                    {
                        parent.passSendingPacket(acknowledgement);
                    }
                    catch (IOException e)
                    {
                        System.err.println("Failed to send packet, exiting");
                        System.exit(1);
                    }
                }
            }
        }
    }
    
    class StoredPacket
    {
        public DatagramPacket packet;
        public int sequenceNumber;
        
        public StoredPacket(DatagramPacket newPacket, int newSequenceNumber)
        {
            packet = newPacket;
            sequenceNumber = newSequenceNumber;
        }
    }

	private boolean stillSending;
    
    private int sequenceNumberSend;
    private int sequenceNumberReceive;
    private List outgoingPackets;
    private List incomingPackets;
    
    private IncomingPacketCatcher receiver;

    // Call inherited constructors
    public ReliableDatagramSocket() throws SocketException 
    {
        super();

		stillSending = true;
        sequenceNumberSend = 0;
        sequenceNumberReceive = 0;
        outgoingPackets = new ArrayList();
        incomingPackets = new ArrayList();
        
        //start receive thread
        receiver = new IncomingPacketCatcher(this);
        receiver.start();
    }


    public ReliableDatagramSocket( int port ) throws SocketException
    {
        super( port );

		stillSending = true;
        sequenceNumberSend = 0;
        sequenceNumberReceive = 0;
        outgoingPackets = new ArrayList();
        incomingPackets = new ArrayList();
        
        //start receive thread
        receiver = new IncomingPacketCatcher(this);
        receiver.start();
    }

	public void close()
	{
		//loop until all packets have been sent properly
		while(!outgoingPackets.isEmpty())
		{
		}
		stillSending = false;

		try
		{
			Thread.currentThread().sleep(3500);
		}
		catch (InterruptedException e)
		{
			System.err.println("Somehow the wait was interupted.....exiting");
			super.close();
			System.exit(1);
		}
		super.close();
	}

    // Override send method to append RDP headers/trailers etc
    public void send(DatagramPacket p) throws IOException
    {
        // Do stuff here
        
        CRC32 checksum = new CRC32();
        checksum.update(p.getData(), 0, p.getLength());
        
        //add my header to the packet
        String header = checksum.getValue() + "&" + sequenceNumberSend + "*";
        byte[] headerAsBytes = header.getBytes();
        byte[] dataAsBytes = p.getData();
        
        byte[] dataWithHeader = new byte[headerAsBytes.length + p.getLength()];
        int index = 0;
        for(int i = 0; i < headerAsBytes.length; i++)
        {
             dataWithHeader[index] = headerAsBytes[i];
             index++;
        }
        for(int i = 0; i < p.getLength(); i++)
        {
             dataWithHeader[index] = dataAsBytes[i];
             index++;
        }
        
        DatagramPacket outgoingPacket = new DatagramPacket(dataWithHeader, dataWithHeader.length, p.getAddress(), p.getPort());

		addOutgoingPacket(new StoredPacket(outgoingPacket, sequenceNumberSend));
        sequenceNumberSend++;
                
        /*DEBUG
        System.err.println(new String(p.getData()));
        System.err.println("Packet sent");*/

        super.send(outgoingPacket); // do not delete this line!!
    }

    // Override receive method 
    public void receive(DatagramPacket p) throws IOException
    {
        // Do stuff here
        
        while(incomingPackets.isEmpty())
        {
            //TESTING
            //System.out.println("Nothing to pass up");
        }
        DatagramPacket oldestPacket = (DatagramPacket)incomingPackets.get(0);
        incomingPackets.remove(0);
        
        //set p to be the first packet in the list
        byte[] toFillFrom = oldestPacket.getData();
        byte[] toFill = p.getData();
        for(int i = 0; i < oldestPacket.getLength(); i++)
        {
            toFill[i] = toFillFrom[i];
        }
        p.setLength(oldestPacket.getLength());
        p.setAddress(oldestPacket.getAddress());
        p.setPort(oldestPacket.getPort());
        
        //TESTING System.out.println("Passing up : " + new String(p.getData()));
        
        //super.receive( p ); // do not delete this line!!
    }
    
    private void passReceivedPacket(DatagramPacket incomingPacket) throws IOException
    {
        super.receive(incomingPacket);
    }
    
    private void passSendingPacket(DatagramPacket outgoingPacket) throws IOException
    {
        super.send(outgoingPacket);
    }
    
    private void passTimeoutSet(int timeout) throws SocketException
    {
        super.setSoTimeout(timeout);
    }

	synchronized private void addOutgoingPacket(StoredPacket newestPacket)
	{
		outgoingPackets.add(newestPacket);
	}
    
    synchronized private void removeOutgoingPackets(int latestSequenceNumber)
    {
        int currentIndex = 0;
        while(currentIndex < outgoingPackets.size())
        {
            StoredPacket currentPacket = (StoredPacket)outgoingPackets.get(currentIndex);
            if(currentPacket.sequenceNumber <= latestSequenceNumber)
            {
                outgoingPackets.remove(currentIndex);
            }
            else
            {
                currentIndex++;
            }
        }
    }
    
    synchronized private void resendOutgoingPackets() throws IOException
    {
        Iterator packetList = outgoingPackets.iterator();
		int counter = 0;
        while(packetList.hasNext())// for simulating sliding window && counter < 5)
        {
            StoredPacket currentPacket = (StoredPacket)packetList.next();
            
            super.send(currentPacket.packet);
		
			counter++;
        }
		//TESTING
		System.out.println("Resent " + counter + " packets");
    }
}
