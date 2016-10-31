from socket import *
import os
import sys
import struct
import time
import select
import binascii  

ICMP_ECHO_REQUEST = 8

def checksum(string): 
	csum = 0
	countTo = (len(string) // 2) * 2
	count = 0
	while count < countTo :
		thisVal = ord(string[count+1]) * 256 + ord(string[count]) 
		csum = csum + thisVal 
		csum = csum & 0xffffffff  
		count = count + 2

	if countTo < len(string) :
		csum = csum + ord(string[len(string) - 1])
		csum = csum & 0xffffffff 

	csum = (csum >> 16) + (csum & 0xffff)
	csum = csum + (csum >> 16)
	answer = ~csum 
	answer = answer & 0xffff 
	answer = answer >> 8 | (answer << 8 & 0xff00)
	return answer

def receiveOnePing(mySocket, ID, timeout, destAddr):
	timeLeft = timeout
	while 1 : 
		startedSelect = time.time()
		whatReady = select.select([mySocket], [], [], timeLeft)
		howLongInSelect = (time.time() - startedSelect)
		if whatReady[0] == [] : # Timeout
			return { 'timedout': True }
		timeReceived = time.time() 
		recPacket, addr = mySocket.recvfrom(1024)
		#Fill in start
		#Fetch the ICMP header from the IP packet
		icmpHeader = recPacket[20:28]
		icmpType, icmpCode, icmpChecksum, icmpId, icmpSequence = struct.unpack("bbHHh", icmpHeader)
		if icmpId == ID :
			byte = struct.calcsize ("d")
			timeSent = struct.unpack("d", recPacket[28:28 + byte])[0]
			return {'ttl': int(binascii.hexlify(recPacket[8]), 16), 'addr': addr, 'packet': recPacket, 'type': icmpType, 'code': icmpCode, 'checksum': icmpChecksum, 'id': icmpId, 'sequence': icmpSequence, 'time': (timeReceived - timeSent) * 1000, 'icmpHeader': icmpHeader}
		#Fill in end
		timeLeft = timeLeft - howLongInSelect
		if timeLeft <= 0 :
			return "Request timed out."


def sendOnePing(mySocket, destAddr, ID):
	# Header is type (8), code (8), checksum (16), id (16), sequence (16)

	myChecksum = 0
	# Make a dummy header with a 0 checksum
	# struct -- Interpret strings as packed binary data
	header = struct.pack("bbHHh", ICMP_ECHO_REQUEST, 0, myChecksum, ID, 1)
	data = struct.pack("d", time.time())
	# Calculate the checksum on the data and the dummy header.
	myChecksum = checksum(str(header + data))

	# Get the right checksum, and put in the header
	if sys.platform == 'darwin' :
		# Convert 16-bit integers from host to network  byte order
		myChecksum = htons(myChecksum) & 0xffff		
	else :
		myChecksum = htons(myChecksum)

	header = struct.pack("bbHHh", ICMP_ECHO_REQUEST, 0, myChecksum, ID, 1)
	packet = header + data

	mySocket.sendto(packet, (destAddr, 1)) # AF_INET address must be tuple, not str
	# Both LISTS and TUPLES consist of a number of objects
	# which can be referenced by their position number within the object.

def doOnePing(destAddr, timeout): 
	icmp = getprotobyname("icmp")
	# SOCK_RAW is a powerful socket type. For more details:   
	# http://sock-raw.org/papers/sock_raw

	mySocket = socket(AF_INET, SOCK_RAW, icmp)

	myID = os.getpid() & 0xFFFF  # Return the current process i
	sendOnePing(mySocket, destAddr, myID)
	delay = receiveOnePing(mySocket, myID, timeout, destAddr)

	mySocket.close()
	return delay

def printStatistics(numTransmitted, numReceived, rtts):
	print("--- localhost ping statistics ---")
	maxRtt = float(0)
	minRtt = float(0)
	totalRtt = float(0)
	for rtt in rtts :
		if rtt > maxRtt :
			maxRtt = rtt
		elif minRtt > rtt or minRtt == 0:
			minRtt = rtt
		totalRtt += float(rtt)
	avgRtt = totalRtt / float(len(rtts))
	packetLoss = float(0)
	if numTransmitted > numReceived :
		packetLoss = float(numTransmitted - numReceived) / float(numTransmitted) * 100
	print("%d packets transmitted, %d packets received, %.3f%% packet loss" % (numTransmitted, numReceived, packetLoss * float(100)))
	print("round-trip min/avg/max/stddev = %.3f/%.3f/%.3f/f ms" % (minRtt, avgRtt, maxRtt))

def ping(host, timeout=1):
	# timeout=1 means: If one second goes by without a reply from the server,
	# the client assumes that either the client's ping or the server's pong is lost
	dest = gethostbyname(host)
	rtts = list()
	numTransmitted = 0
	print("Pinging " + dest + " using Python:")
	print("")
	# Send ping requests to a server separated by approximately one second
	for index in range(0, 10) :
		delay = doOnePing(dest, timeout)
		if 'timedout' in delay :
			print("Request timeout")
		else :
			print("Reply from %s: bytes=%d time=%fms TTL=%d" % (delay['addr'], len(delay['packet']), delay['time'], delay['ttl']))
			rtts.append(delay['time'])
		numTransmitted += 1
		time.sleep(1)# one second
	printStatistics(numTransmitted, len(rtts), rtts)

#ping("google.com")
ping("localhost")