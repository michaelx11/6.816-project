import java.util.*;
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

class SerialFirewallWorker implements FirewallWorker {
  PaddedPrimitiveNonVolatile<Boolean> done;
  public Set<Integer> sourceSet;
  public Set<Integer> pairSet;
  public int[] histogram;
  public PacketGenerator source;
  long totalPackets = 0;

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
