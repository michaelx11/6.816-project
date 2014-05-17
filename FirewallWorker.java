import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.*;
import org.deuce.Atomic;
import java.util.concurrent.locks.ReentrantLock;

interface FirewallWorker extends Runnable {
  public void run();
}

class FirewallStruct {
  public Set<Integer> sourceSet;
  public Set<Integer> pairSet;
  public int[] histogram;

  public FirewallStruct() {
    sourceSet = new HashSet<Integer>();
    pairSet = new HashSet<Integer>();
    histogram = new int[1 << 16];
  }
}

// COMPRESSED DATA STRUCTURE
class CompressedFirewallStruct {
  public final int logNumLocks = 12;
  public boolean isCompressed;
  public BitSet[] R;
  public BitSet[] valid;
  public long[] png;

  public final long[] longLMasks = new long[65];
  public final long[] longRMasks = new long[65];
  public final int[] intLMasks = new int[33];
  public final int[] intRMasks = new int[33];

  public HashMap<Integer, Integer>[] overflow;

  public ReentrantLock[] locks;
  public ReentrantLock[] histoLocks;
  public final int lockMask;

  public AtomicInteger[] histogram;

  public CompressedFirewallStruct(int logNumAddresses) {
    // Choose between bit-wise or compressed representation
    if (logNumAddresses < 16) {
      isCompressed = false;
      png = new long[1 << logNumAddresses];
      R = new BitSet[1 << logNumAddresses];
      for (int u = 0; u < R.length; u++) {
        R[u] = new BitSet(1 << logNumAddresses);
        R[u].set(0, R[u].length(), true);
      }
    } else {
      isCompressed = true;
      png = new long[1 << logNumAddresses];
      R = new BitSet[1 << logNumAddresses];
      valid = new BitSet[1 << logNumAddresses]; 
      for (int u = 0; u < R.length; u++) {
        R[u] = new BitSet(1 << (logNumAddresses - 5));
        R[u].set(0, 1 << (logNumAddresses - 5));
        valid[u] = new BitSet(1 << (logNumAddresses - 5));
        valid[u].set(0, 1 << (logNumAddresses - 5));
      }
      overflow = new HashMap[1 << logNumAddresses];
      for (int i = 0; i < (1 << logNumAddresses); i++) {
        overflow[i] = new HashMap<Integer, Integer>(300);
      }
    }
    // initialize locks
    locks = new ReentrantLock[1 << logNumLocks];
    histoLocks = new ReentrantLock[1 << logNumLocks];
    for (int i = 0; i < (1 << logNumLocks); i++) {
      locks[i] = new ReentrantLock();
      histoLocks[i] = new ReentrantLock();
    }

    lockMask = (1 << logNumLocks) - 1;

    // histogram
    histogram = new AtomicInteger[1 << 16];
    for (int i = 0; i < (1<<16); i++)
      histogram[i] = new AtomicInteger();

    longLMasks[0] = 0L;
    longRMasks[64] = 0L;
    for (int i = 0; i < 64; i++)
      longLMasks[i] = ~((1L << (64 - i - 1)) - 1);

    for (int i = 0; i < 64; i++)
      longRMasks[i] = (1L << (64 - i)) - 1;

    intLMasks[0] = 0;
    intRMasks[32] = 0;
    for (int i = 0; i < 32; i++)
      intLMasks[i] = ~((1 << (32 - i - 1)) - 1);

    for (int i = 0; i < 32; i++)
      intRMasks[i] = ((1 << (32 - i)) - 1);
  }
}

// PARALLEL FIREWALL WORKER
class FastFirewallWorker implements FirewallWorker {
  public long[] longLMasks;
  public long[] longRMasks;
  public int[] intLMasks;
  public int[] intRMasks;

  PaddedPrimitiveNonVolatile<Boolean> done;
  LamportQueue<Packet>[] queues;
  final int queueNum;
  final int numWorkers;
  ReentrantLock[] queueLocks;
  public AtomicInteger[] histogram;

  public BitSet[] R;
  public BitSet[] valid;
  public long[] png;

  public HashMap<Integer, Integer>[] overflow;
  public boolean isCompressed;

  public ReentrantLock[] locks;
  public ReentrantLock[] histoLocks;
  public final int lockMask;
  public final int logNumLocks;
  public long totalPackets;
  public long numDataPackets;
  public long numConfigPackets;
 
  public FastFirewallWorker(
      PaddedPrimitiveNonVolatile<Boolean> done, 
      LamportQueue<Packet>[] queues,
      ReentrantLock[] queueLocks,
      int numWorkers,
      int queueNum,
      CompressedFirewallStruct state) {
    super();
    this.done = done;
    this.queues = queues;
    this.numWorkers = numWorkers;
    this.queueLocks = queueLocks;
    this.queueNum = queueNum;

    this.R = state.R;
    this.png = state.png;
    this.valid = state.valid;
    this.histogram = state.histogram;
    this.overflow = state.overflow;
    this.locks = state.locks;
    this.logNumLocks = state.logNumLocks;
    this.lockMask = state.lockMask;
    this.isCompressed = state.isCompressed;
    this.longLMasks = state.longLMasks;
    this.longRMasks = state.longRMasks;
    this.intLMasks = state.intLMasks;
    this.intRMasks = state.intRMasks;
    this.histoLocks = state.histoLocks;
  }

  public void run() {
    Packet pkt;
    boolean foundQueue = false;
    int randomNum = (int)(Math.random() * numWorkers);
    while(!done.value) {
      if (!foundQueue) {
        randomNum = (int)(Math.random() * numWorkers);
        if (queueLocks[randomNum].tryLock()) {
          foundQueue = true;
          queueLocks[randomNum].unlock();
        }
      } else {
        try {
          queueLocks[randomNum].lock();
          pkt = queues[randomNum].deq();
          totalPackets++;
          handlePacket(pkt);
        } catch (EmptyException e) {
          foundQueue = false;
        } finally {
          queueLocks[randomNum].unlock();
        }
      }
    }

    for (int u = 0; u < 8; u++) {
      try {
        pkt = queues[queueNum].deq();
        totalPackets++;
        handlePacket(pkt);
      } catch (EmptyException e) {
        break;
      }
    }
  }

  // Called under lock
  public void updatePNG(int addr, boolean status) {
    png[addr] = (status ? 1 : 0);
  }

  public void setR(int addr, int bAddr, int eAddr) {
    if (isCompressed) {
      final int beginBucket = bAddr >> 5;
      final int endBucket = eAddr >> 5;
      
      // Handle beginning bucket
      int beginValue = 0;
      if (!valid[addr].get(beginBucket)) {
        beginValue = overflow[addr].get(beginBucket);
      } else {
        beginValue = (R[addr].get(beginBucket) ? ~0 : 0);
      }
      beginValue |=  (1 << (32 - (bAddr & (31)))) - 1;
      if (beginValue != ~0) {
        overflow[addr].put(beginBucket, beginValue);
        valid[addr].clear(beginBucket);
      } else {
      // Completely filled
        R[addr].set(beginBucket);
        valid[addr].set(beginBucket);
      }

      // Handle ending bucket
      int endValue = 0;
      if (!valid[addr].get(endBucket)) {
        endValue = overflow[addr].get(endBucket);
      } else {
        endValue = (R[addr].get(endBucket) ? ~0 : 0);
      }
      endValue |=  ~((1 << (31 - (eAddr & 31))) - 1);
      if (endValue != ~0) {
        overflow[addr].put(endBucket, endValue);
        valid[addr].clear(endBucket);
      } else {
      // Completely filled
        R[addr].set(endBucket);
        valid[addr].set(endBucket);
      }

      if (endBucket - beginBucket > 1) {
        R[addr].set(beginBucket + 1, endBucket);
        valid[addr].set(beginBucket + 1, endBucket);
      }
    } else {
      R[addr].set(bAddr, eAddr);
    }
  }

  public void clearR(int addr, int bAddr, int eAddr) {
    if (isCompressed) {
      final int beginBucket = bAddr >> 5;
      final int endBucket = eAddr >> 5;
      
      // Handle beginning bucket
      int beginValue = 0;
      if (!valid[addr].get(beginBucket)) {
        beginValue = overflow[addr].get(beginBucket);
      } else {
        beginValue = (R[addr].get(beginBucket) ? ~0 : 0);
      }
      beginValue = (beginValue & ((1 << (32 - (bAddr & (31)))) - 1));
      if (beginValue != 0) {
        overflow[addr].put(beginBucket, beginValue);
        valid[addr].clear(beginBucket);
      } else {
      // Completely filled
        R[addr].clear(beginBucket);
        valid[addr].set(beginBucket);
      }

      // Handle ending bucket
      int endValue = 0;
      if (!valid[addr].get(endBucket)) {
        endValue = overflow[addr].get(endBucket);
      } else {
        endValue = (R[addr].get(endBucket) ? ~0 : 0);
      }
      endValue = (endValue & ( ~((1 << (31 - (eAddr & 31))) - 1)));
      if (endValue != 0) {
        overflow[addr].put(endBucket, endValue);
        valid[addr].clear(endBucket);
      } else {
      // Completely filled
        R[addr].clear(endBucket);
        valid[addr].set(endBucket);
      }

      if (endBucket - beginBucket > 1) {
        R[addr].clear(beginBucket + 1, endBucket);
        valid[addr].set(beginBucket + 1, endBucket);
      }
    } else {
      R[addr].clear(bAddr, eAddr);
    }
  }

  public boolean checkPNG(int source) {
    return png[source] == 1;
  }

  public boolean checkR(int source, int dest) {
    if (isCompressed) {
      final int bucket = source >> 5;
      // Is valid?
      if (valid[dest].get(bucket)) {
        return (R[dest].get(bucket) ? true : false);
      } else {
        final int value = overflow[dest].get(bucket);
        return ((value & (1 << (source & (31)))) != 0 ? true : false);
      }
    } else {
      return R[dest].get(source);
    }
  }


  public void processConfigPacket(Packet pkt) {
    final int address = pkt.config.address;
    final boolean pngStatus = pkt.config.personaNonGrata;
    final int beginAddr = pkt.config.addressBegin;
    final int endAddr = pkt.config.addressEnd;
    final boolean acceptingRange = pkt.config.acceptingRange;

//    System.out.printf("addr: %d, interval: [%d - %d], png: %b, accepting: %b\n", address, beginAddr, endAddr, pngStatus, acceptingRange);

    final int lockIndex = address & lockMask;
    try {
      locks[lockIndex].lock();
      updatePNG(address, pngStatus);

      if (acceptingRange) {
        setR(address, beginAddr, endAddr);
      } else {
        clearR(address, beginAddr, endAddr);
      }
    } finally {
      locks[lockIndex].unlock();
    }
  }

  public boolean checkPermissions(int source, int dest) {
    return !checkPNG(source) && checkR(source, dest);
  }

  public void processDataPacket(Packet pkt) {
    Header hdr = pkt.header;
    final int lockIndex = pkt.header.dest & lockMask;
    try {
      locks[lockIndex].lock();
      long checksum = 0;
      if (!checkPNG(hdr.source) && checkR(hdr.source, hdr.dest)) {
        checksum = Fingerprint.getFingerprint(pkt.body.iterations, pkt.body.seed);
      }
      histogram[(int)checksum].getAndIncrement();
    } finally {
      locks[lockIndex].unlock();
    }
  }

  public void handlePacket(Packet pkt) {
    switch (pkt.type) {
      case ConfigPacket: processConfigPacket(pkt); break;
      case DataPacket: processDataPacket(pkt); break;
    }
  }
}

class SerialFirewallWorker implements FirewallWorker {
  PaddedPrimitiveNonVolatile<Boolean> done;
  public Set<Integer> sourceSet;
  public Set<Integer> pairSet;
  public int[] histogram;
  public PacketGenerator source;
  long totalPackets = 0;
  long totalInterval = 0;
  long totalConfig = 0;

  public SerialFirewallWorker() {}

  public SerialFirewallWorker(
    PaddedPrimitiveNonVolatile<Boolean> done,
    PacketGenerator source,
    FirewallStruct state) {
    this.done = done;
    this.source = source;
    this.sourceSet = state.sourceSet;
    this.pairSet = state.pairSet;
    this.histogram = state.histogram;
  }

  public void run() {
    Packet pkt;
    while( !done.value ) {
      totalPackets++;
      pkt = source.getPacket();
      handlePacket(pkt);
    }
  }

  public void handlePacket(Packet pkt) {
    switch (pkt.type) {
      case ConfigPacket: processConfigPacket(pkt); break;
      case DataPacket: processDataPacket(pkt); break;
    }
  }

  public void updateSources(int addr, boolean status) {
    if (status) {
      sourceSet.add(addr);
    } else {
      sourceSet.remove(addr);
    }
  }

  public void updateDestMap(int addr, int addrB, int addrE, boolean status) {
    totalInterval += addrE - addrB + 1;
    System.out.println(addrB + " " + addrE);
    totalConfig++;
    for (int i = addrB; i < addrE; i++) {
      final int x = (addr << 16 | (i & 0xFFFF));
      if (status) {
        pairSet.remove(x);
      } else {
        pairSet.add(x);
      }
    }
  }

  public void processConfigPacket(Packet pkt) {
    Config conf = pkt.config;
    updateSources(conf.address, conf.personaNonGrata);
    updateDestMap(conf.address, conf.addressBegin, conf.addressEnd, conf.acceptingRange);
  }

  public boolean checkPNG(int sourceAddr) {
    return sourceSet.contains(sourceAddr);
  }

  public boolean checkR(int sAddr, int dAddr) {
    int x = (dAddr << 16 | (sAddr & 0xFFFF));
    return !pairSet.contains(x);
  }

  public void processDataPacket(Packet pkt) {
    Header hdr = pkt.header;
    if (!checkPNG(hdr.source) && checkR(hdr.source, hdr.dest)) {
      long checksum = Fingerprint.getFingerprint(pkt.body.iterations, pkt.body.seed);
      histogram[(int)checksum]++;
    }
  }

  public static void main(String ... args) throws IOException {
  
  }
}

class STMFirewallWorker extends SerialFirewallWorker {
  PaddedPrimitiveNonVolatile<Boolean> done;
  LamportQueue<Packet>[] queues;
  final int queueNum;
  final int numWorkers;
  ReentrantLock[] locks;
 
  public STMFirewallWorker(
      PaddedPrimitiveNonVolatile<Boolean> done, 
      LamportQueue<Packet>[] queues,
      ReentrantLock[] locks,
      int numWorkers,
      int queueNum,
      FirewallStruct state) {
    super();
    this.done = done;
    this.queues = queues;
    this.numWorkers = numWorkers;
    this.locks = locks;
    this.queueNum = queueNum;
    this.sourceSet = state.sourceSet;
    this.pairSet = state.pairSet;
    this.histogram = state.histogram;
  }

  @Atomic
  public void processConfigPacket(Packet pkt) {
    super.processConfigPacket(pkt);
  }

  @Atomic
  public void processDataPacket(Packet pkt) {
    super.processDataPacket(pkt);
  }

  public void run() {
    Packet pkt;
    boolean foundQueue = false;
    int randomNum = (int)(Math.random() * numWorkers);
    while(!done.value) {
      if (!foundQueue) {
      randomNum = (int)(Math.random() * numWorkers);
      foundQueue = true;
      } else {
        try {
          locks[randomNum].lock();
          pkt = queues[randomNum].deq();
          totalPackets++;
          handlePacket(pkt);
        } catch (EmptyException e) {
          foundQueue = false;
        } finally {
          locks[randomNum].unlock();
        }
      }
    }

    for (int u = 0; u < 8; u++) {
      try {
        pkt = queues[queueNum].deq();
        totalPackets++;
        handlePacket(pkt);
      } catch (EmptyException e) {
        break;
      }
    }
  }
}

// FAST SERIAL WORKER
class SerialFastFirewallWorker extends FastFirewallWorker {
  PacketGenerator source;

  public SerialFastFirewallWorker(
      PaddedPrimitiveNonVolatile<Boolean> done, 
      LamportQueue<Packet>[] queues,
      ReentrantLock[] queueLocks,
      int numWorkers,
      int queueNum,
      CompressedFirewallStruct state) {
    super(done, queues, queueLocks, numWorkers, queueNum, state);
  }

  public void setSource(PacketGenerator source) {
    this.source = source;
  }

  public void run() {
    Packet pkt;
    while( !done.value ) {
      totalPackets++;
      pkt = source.getPacket();
      handlePacket(pkt);
    }
  }

  public void processConfigPacket(Packet pkt) {
    final int address = pkt.config.address;
    final boolean pngStatus = pkt.config.personaNonGrata;
    final int beginAddr = pkt.config.addressBegin;
    final int endAddr = pkt.config.addressEnd;
    final boolean acceptingRange = pkt.config.acceptingRange;

    updatePNG(address, pngStatus);

    if (acceptingRange) {
      setR(address, beginAddr, endAddr);
    } else {
      clearR(address, beginAddr, endAddr);
    }
  }

  public void processDataPacket(Packet pkt) {
    Header hdr = pkt.header;
    if (!checkPNG(hdr.source) && checkR(hdr.source, hdr.dest)) {
      long checksum = Fingerprint.getFingerprint(pkt.body.iterations, pkt.body.seed);
      final int lockIndex = (int)checksum & lockMask;
      histogram[(int)checksum].getAndIncrement();
    }
  }
}

// FAST STM WORKER
class STMFastFirewallWorker extends FastFirewallWorker {

  public STMFastFirewallWorker(
      PaddedPrimitiveNonVolatile<Boolean> done, 
      LamportQueue<Packet>[] queues,
      ReentrantLock[] queueLocks,
      int numWorkers,
      int queueNum,
      CompressedFirewallStruct state) {
    super(done, queues, queueLocks, numWorkers, queueNum, state);
  }


  @Atomic
  public void processConfigPacket(Packet pkt) {
    final int address = pkt.config.address;
    final boolean pngStatus = pkt.config.personaNonGrata;
    final int beginAddr = pkt.config.addressBegin;
    final int endAddr = pkt.config.addressEnd;
    final boolean acceptingRange = pkt.config.acceptingRange;

    updatePNG(address, pngStatus);

    if (acceptingRange) {
      setR(address, beginAddr, endAddr);
    } else {
      clearR(address, beginAddr, endAddr);
    }
  }

  @Atomic
  public void processDataPacket(Packet pkt) {
    Header hdr = pkt.header;
    if (!checkPNG(hdr.source) && checkR(hdr.source, hdr.dest)) {
      long checksum = Fingerprint.getFingerprint(pkt.body.iterations, pkt.body.seed);
      final int lockIndex = (int)checksum & lockMask;
      histogram[(int)checksum].getAndIncrement();
    }
  }

}

// FAST PIPELINE WORKER
class PipelineFastFirewallWorker extends FastFirewallWorker {
  LamportQueue<Packet>[] packetQueues;

  public PipelineFastFirewallWorker(
      PaddedPrimitiveNonVolatile<Boolean> done, 
      LamportQueue<Packet>[] queues,
      ReentrantLock[] queueLocks,
      int numWorkers,
      int queueNum,
      LamportQueue<Packet>[] packetQueues,
      CompressedFirewallStruct state) {
    super(done, queues, queueLocks, numWorkers, queueNum, state);
    this.packetQueues = packetQueues;
  }

  public void run() {
    Packet pkt;
    boolean foundQueue = false;
    int randomNum = (int)(Math.random() * numWorkers);
    while(!done.value) {
      if (!foundQueue) {
      randomNum = (int)(Math.random() * numWorkers);
      foundQueue = true;
      } else {
        try {
          locks[randomNum].lock();
          pkt = queues[randomNum].deq();
          totalPackets++;
          if (firewallPacket(pkt)) {
            while(true) {
              try {
                packetQueues[randomNum].enq(pkt);
                break;
              } catch(FullException e) {
              }
            }
          }
        } catch (EmptyException e) {
          foundQueue = false;
        } finally {
          locks[randomNum].unlock();
        }
      }
    }

    for (int u = 0; u < 8; u++) {
      try {
        pkt = queues[queueNum].deq();
        totalPackets++;
        handlePacket(pkt);
      } catch (EmptyException e) {
        break;
      }
    }
  }

  public boolean firewallConfigPacket(Packet pkt) {
    final int address = pkt.config.address;
    final boolean pngStatus = pkt.config.personaNonGrata;
    final int beginAddr = pkt.config.addressBegin;
    final int endAddr = pkt.config.addressEnd;
    final boolean acceptingRange = pkt.config.acceptingRange;

    updatePNG(address, pngStatus);

    if (acceptingRange) {
      setR(address, beginAddr, endAddr);
    } else {
      clearR(address, beginAddr, endAddr);
    }
    return false;
  }

  public boolean firewallDataPacket(Packet pkt) {
    Header hdr = pkt.header;
    return (!checkPNG(hdr.source) && checkR(hdr.source, hdr.dest));
  }

  public boolean firewallPacket(Packet pkt) {
    switch (pkt.type) {
      case ConfigPacket: return firewallConfigPacket(pkt);
      case DataPacket: return firewallDataPacket(pkt);
    }
    return false;
  }
}

// FAST PACKET WORKER
class PipelineFastPacketWorker extends FastFirewallWorker {

  public PipelineFastPacketWorker(
      PaddedPrimitiveNonVolatile<Boolean> done, 
      LamportQueue<Packet>[] queues,
      ReentrantLock[] queueLocks,
      int numWorkers,
      int queueNum,
      CompressedFirewallStruct state) {
    super(done, queues, queueLocks, numWorkers, queueNum, state);
  }

  public void run() {
    Packet pkt;
    boolean foundQueue = false;
    int randomNum = (int)(Math.random() * numWorkers);
    while(!done.value) {
      if (!foundQueue) {
      randomNum = (int)(Math.random() * numWorkers);
      foundQueue = true;
      } else {
        try {
          locks[randomNum].lock();
          pkt = queues[randomNum].deq();
          processDataPacket(pkt);
          totalPackets++;
        } catch (EmptyException e) {
          foundQueue = false;
        } finally {
          locks[randomNum].unlock();
        }
      }
    }

    for (int u = 0; u < 8; u++) {
      try {
        pkt = queues[queueNum].deq();
        totalPackets++;
        handlePacket(pkt);
      } catch (EmptyException e) {
        break;
      }
    }
  }

  public void processDataPacket(Packet pkt) {
    Header hdr = pkt.header;
    if (!checkPNG(hdr.source) && checkR(hdr.source, hdr.dest)) {
      long checksum = Fingerprint.getFingerprint(pkt.body.iterations, pkt.body.seed);
      final int lockIndex = (int)checksum & lockMask;
      histogram[(int)checksum].getAndIncrement();
    }
  }
}
