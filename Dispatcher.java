import java.util.*;
import java.io.*;

class Dispatcher implements Runnable {

  PaddedPrimitiveNonVolatile<Boolean> done;
  final PacketGenerator pkt;
  long totalPackets = 0;
  long numFullExceptions = 0;
  final int numSources;
  final LamportQueue<Packet>[] queues;

  public Dispatcher(
    PaddedPrimitiveNonVolatile<Boolean> done,
    PacketGenerator pkt,
    int numSources,
    LamportQueue<Packet>[] queues) {

    this.done = done;
    this.pkt = pkt;
    this.numSources = numSources;
    this.queues = queues;
  }

  public void run() {
    int limit = 15;
    Packet tmp;
//    while(!done.value && totalPackets < limit) {
    while(!done.value) {
      for (int i = 0; i < numSources; i++) {
//        System.out.println("Dispatched Packet: " + i);
        tmp = pkt.getPacket();
        
        // Never drop a packet
        while (true) {
          try {
            queues[i].enq(tmp);
            break;
          } catch (FullException e) {
            numFullExceptions++;
          }
        }
        totalPackets++;
      }
    }
  }
}
