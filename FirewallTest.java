import java.util.*;
import java.io.*;
import java.util.concurrent.locks.ReentrantLock;

class SerialFirewall {
  public static void main(String ... args) throws IOException {
    final int numMilliseconds = Integer.parseInt(args[0]);
    final int numAddressesLog = Integer.parseInt(args[1]);
    final int numTrainsLog = Integer.parseInt(args[2]);
    final double meanTrainSize = Double.parseDouble(args[3]);
    final double meanTrainsPerComm = Double.parseDouble(args[4]);
    final int meanWindow = Integer.parseInt(args[5]);
    final int meanCommsPerAddress = Integer.parseInt(args[6]);
    final int meanWork = Integer.parseInt(args[7]);
    final double configFraction = Double.parseDouble(args[8]);
    final double pngFraction = Double.parseDouble(args[9]);
    final double acceptingFraction = Double.parseDouble(args[10]);

    @SuppressWarnings({"unchecked"})
    StopWatch timer = new StopWatch();
    PacketGenerator source = new PacketGenerator(numAddressesLog,
        numTrainsLog, meanTrainSize, meanTrainsPerComm, meanWindow,
        meanCommsPerAddress, meanWork, configFraction, pngFraction, acceptingFraction);

    PaddedPrimitiveNonVolatile<Boolean> done = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> memFence = new PaddedPrimitive<Boolean>(false);

    FirewallStruct state = new FirewallStruct();
    SerialFirewallWorker initializer = new SerialFirewallWorker(done, source, state);
    int limit = (int)Math.pow(1 << numAddressesLog, 3.0/2.0);
    System.out.println("Limit: " + limit);
    for (int i = 0; i < limit; i++) {
      if (i % 100000 == 0) {
        System.out.println(i);
        System.out.printf("Size: PNG: %d, R: %d\n", state.sourceSet.size(), state.pairSet.size());
      }

      initializer.processConfigPacket(source.getConfigPacket());
    }
    System.out.println("Finished initialization");

    SerialFirewallWorker workerData = new SerialFirewallWorker(done, source, state);
    Thread workerThread = new Thread(workerData);

    workerThread.start();
    timer.startTimer();
    try {
      Thread.sleep(numMilliseconds);
    } catch (InterruptedException ignore) {;}
    done.value = true;
    memFence.value = true;
    try {
      workerThread.join();
    } catch (InterruptedException ignore) {;}      
    timer.stopTimer();
    final long totalCount = workerData.totalPackets;
    System.out.println("count: " + totalCount);
    System.out.println("time: " + timer.getElapsedTime());
    System.out.println(totalCount/timer.getElapsedTime() + " pkts / ms");
    System.out.println("average interval: " + ((double)workerData.totalInterval/workerData.totalConfig));
  }
}

class CompressedSerialFirewall {
  public static void main(String ... args) throws IOException {
    final int numMilliseconds = Integer.parseInt(args[0]);
    final int numAddressesLog = Integer.parseInt(args[1]);
    final int numTrainsLog = Integer.parseInt(args[2]);
    final double meanTrainSize = Double.parseDouble(args[3]);
    final double meanTrainsPerComm = Double.parseDouble(args[4]);
    final int meanWindow = Integer.parseInt(args[5]);
    final int meanCommsPerAddress = Integer.parseInt(args[6]);
    final int meanWork = Integer.parseInt(args[7]);
    final double configFraction = Double.parseDouble(args[8]);
    final double pngFraction = Double.parseDouble(args[9]);
    final double acceptingFraction = Double.parseDouble(args[10]);

    @SuppressWarnings({"unchecked"})
    StopWatch timer = new StopWatch();
    PacketGenerator source = new PacketGenerator(numAddressesLog,
        numTrainsLog, meanTrainSize, meanTrainsPerComm, meanWindow,
        meanCommsPerAddress, meanWork, configFraction, pngFraction, acceptingFraction);

    PaddedPrimitiveNonVolatile<Boolean> done = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> memFence = new PaddedPrimitive<Boolean>(false);

    // PLACEHOLDERS
    // allocate and initialize locks and any signals used to marshal threads (eg. done signals)
    ReentrantLock[] locks = new ReentrantLock[1];;
    for (int i = 0; i < 1; i++) {
      locks[i] = new ReentrantLock();
    }

    //
    // allocate and initialize Lamport queues and hash table
    //
    LamportQueue<Packet>[] queues = new LamportQueue[1];

    for (int i = 0; i < 1; i++) {
      queues[i] = new LamportQueue<Packet>(8);
    }


    CompressedFirewallStruct state = new CompressedFirewallStruct(numAddressesLog);
    SerialFastFirewallWorker initializer = new SerialFastFirewallWorker(done, queues, locks, 1, -1, state);
    int limit = (int)Math.pow(1 << numAddressesLog, 3.0/2.0);
//    System.out.println("Limit: " + limit);
    for (int i = 0; i < limit; i++) {

      initializer.processConfigPacket(source.getConfigPacket());
    }
//    System.out.println("Finished initialization");

    SerialFastFirewallWorker workerData = new SerialFastFirewallWorker(done, queues, locks, 1, -1, state);
    workerData.setSource(source);
    Thread workerThread = new Thread(workerData);

    workerThread.start();
    timer.startTimer();
    try {
      Thread.sleep(numMilliseconds);
    } catch (InterruptedException ignore) {;}
    done.value = true;
    memFence.value = true;
    try {
      workerThread.join();
    } catch (InterruptedException ignore) {;}      
    timer.stopTimer();
    final long totalCount = workerData.totalPackets;
    System.out.println("count: " + totalCount);
    System.out.println("time: " + timer.getElapsedTime());
    System.out.println(totalCount/timer.getElapsedTime() + " pkts / ms");
  }
}
class STMFirewall {
  public static void main(String ... args) throws IOException {
    final int numMilliseconds = Integer.parseInt(args[0]);
    final int numAddressesLog = Integer.parseInt(args[1]);
    final int numTrainsLog = Integer.parseInt(args[2]);
    final double meanTrainSize = Double.parseDouble(args[3]);
    final double meanTrainsPerComm = Double.parseDouble(args[4]);
    final int meanWindow = Integer.parseInt(args[5]);
    final int meanCommsPerAddress = Integer.parseInt(args[6]);
    final int meanWork = Integer.parseInt(args[7]);
    final double configFraction = Double.parseDouble(args[8]);
    final double pngFraction = Double.parseDouble(args[9]);
    final double acceptingFraction = Double.parseDouble(args[10]);
    final int numWorkers = Integer.parseInt(args[11]);

    @SuppressWarnings({"unchecked"})
    StopWatch timer = new StopWatch();
    PacketGenerator source = new PacketGenerator(numAddressesLog,
        numTrainsLog, meanTrainSize, meanTrainsPerComm, meanWindow,
        meanCommsPerAddress, meanWork, configFraction, pngFraction, acceptingFraction);

    // 
    // Dispatcher
    PaddedPrimitiveNonVolatile<Boolean> dispatcherDone = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> dMemFence = new PaddedPrimitive<Boolean>(false);

    // Workers
    PaddedPrimitiveNonVolatile<Boolean> workerDone = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> wMemFence = new PaddedPrimitive<Boolean>(false);

    FirewallStruct state = new FirewallStruct();
    SerialFirewallWorker initializer = new SerialFirewallWorker(workerDone, source, state);
    int limit = (int)Math.pow(1 << numAddressesLog, 3.0/2.0);
    for (int i = 0; i < limit; i++) {
      initializer.processConfigPacket(source.getConfigPacket());
    }

    //
    // allocate and initialize Lamport queues and hash table
    //
    LamportQueue<Packet>[] queues = new LamportQueue[numWorkers];

    for (int i = 0; i < numWorkers; i++) {
      queues[i] = new LamportQueue<Packet>(8);
    }
    // 
    // initialize your hash table w/ initSize number of add() calls using
    // source.getAddPacket();
    //

    // allocate and initialize locks and any signals used to marshal threads (eg. done signals)
    ReentrantLock[] locks = new ReentrantLock[numWorkers];;
    for (int i = 0; i < numWorkers; i++) {
      locks[i] = new ReentrantLock();
      locks[i].lock();
    }

    // allocate and inialize Dispatcher and Worker threads
    //
    STMFirewallWorker[] workers = new STMFirewallWorker[numWorkers];
    Thread[] workerThreads = new Thread[numWorkers];

    Dispatcher dispatchData = new Dispatcher(dispatcherDone, source, numWorkers, queues);
    Thread dispatcherThread = new Thread(dispatchData);

    // call .start() on your Workers
    //
    for (int i = 0; i < numWorkers; i++) {
      workers[i] = new STMFirewallWorker(workerDone, queues, locks, numWorkers, i, state);
      workerThreads[i] = new Thread(workers[i]);

      workerThreads[i].start();
    }

    for (int i = 0; i < numWorkers; i++) {
      locks[i].unlock();
    }

    timer.startTimer();
    //
    // call .start() on your Dispatcher
    //
    dispatcherThread.start();
    try {
      Thread.sleep(numMilliseconds);
    } catch (InterruptedException ignore) {;}
    //
    // assert signals to stop Dispatcher
    dispatcherDone.value = true;
    dMemFence.value = true;
    //
    // call .join() on Dispatcher
    try {
      dispatcherThread.join();
    } catch (InterruptedException ignore) {;}
    //
    // assert signals to stop Workers - they are responsible for leaving
    // the queues empty
    workerDone.value = true;
    wMemFence.value = true;
    for (int i = 0; i < numWorkers; i++) {
      try {
        workerThreads[i].join();
      } catch (InterruptedException ignore) {;}
    }
    // call .join() for each Worker
    //
    timer.stopTimer();
    // report the total number of packets processed and total time
    final long totalCount = dispatchData.totalPackets;
    System.out.println("count: " + totalCount);
    System.out.println("time: " + timer.getElapsedTime());
    System.out.println(totalCount/timer.getElapsedTime() + " pkts / ms");

 
  }
}

class ParallelFirewall {
  public static void main(String ... args) throws IOException {
    final int numMilliseconds = Integer.parseInt(args[0]);
    final int numAddressesLog = Integer.parseInt(args[1]);
    final int numTrainsLog = Integer.parseInt(args[2]);
    final double meanTrainSize = Double.parseDouble(args[3]);
    final double meanTrainsPerComm = Double.parseDouble(args[4]);
    final int meanWindow = Integer.parseInt(args[5]);
    final int meanCommsPerAddress = Integer.parseInt(args[6]);
    final int meanWork = Integer.parseInt(args[7]);
    final double configFraction = Double.parseDouble(args[8]);
    final double pngFraction = Double.parseDouble(args[9]);
    final double acceptingFraction = Double.parseDouble(args[10]);
    final int numWorkers = Integer.parseInt(args[11]);

    @SuppressWarnings({"unchecked"})
    StopWatch timer = new StopWatch();
    PacketGenerator source = new PacketGenerator(numAddressesLog,
        numTrainsLog, meanTrainSize, meanTrainsPerComm, meanWindow,
        meanCommsPerAddress, meanWork, configFraction, pngFraction, acceptingFraction);

    // 
    // Dispatcher
    PaddedPrimitiveNonVolatile<Boolean> dispatcherDone = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> dMemFence = new PaddedPrimitive<Boolean>(false);

    // Workers
    PaddedPrimitiveNonVolatile<Boolean> workerDone = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> wMemFence = new PaddedPrimitive<Boolean>(false);

    // allocate and initialize locks and any signals used to marshal threads (eg. done signals)
    ReentrantLock[] locks = new ReentrantLock[numWorkers];;
    for (int i = 0; i < numWorkers; i++) {
      locks[i] = new ReentrantLock();
//      locks[i].lock();
    }

    //
    // allocate and initialize Lamport queues and hash table
    //
    LamportQueue<Packet>[] queues = new LamportQueue[numWorkers];

    for (int i = 0; i < numWorkers; i++) {
      queues[i] = new LamportQueue<Packet>(8);
    }

    CompressedFirewallStruct state = new CompressedFirewallStruct(numAddressesLog);
    SerialFastFirewallWorker initializer = new SerialFastFirewallWorker(dispatcherDone, queues, locks, 1, -1, state);
    int limit = (int)Math.pow(1 << numAddressesLog, 3.0/2.0);
//    System.out.println(limit);
    for (int i = 0; i < limit; i++) {
//      if (i % 1000000 == 0) {
//        System.out.println(i);
//      }
      initializer.processConfigPacket(source.getConfigPacket());
    }

    // 
    // initialize your hash table w/ initSize number of add() calls using
    // source.getAddPacket();
    //


    // allocate and inialize Dispatcher and Worker threads
    //
    FastFirewallWorker[] workers = new FastFirewallWorker[numWorkers];
    Thread[] workerThreads = new Thread[numWorkers];

    Dispatcher dispatchData = new Dispatcher(dispatcherDone, source, numWorkers, queues);
    Thread dispatcherThread = new Thread(dispatchData);

    // call .start() on your Workers
    //
    for (int i = 0; i < numWorkers; i++) {
      workers[i] = new FastFirewallWorker(workerDone, queues, locks, numWorkers, i, state);
      workerThreads[i] = new Thread(workers[i]);

      workerThreads[i].start();
    }

//    for (int i = 0; i < numWorkers; i++) {
//      locks[i].unlock();
//    }

    timer.startTimer();
    //
    // call .start() on your Dispatcher
    //
    dispatcherThread.start();

    try {
      Thread.sleep(numMilliseconds);
    } catch (InterruptedException ignore) {;}
    //
    // assert signals to stop Dispatcher
    dispatcherDone.value = true;
    dMemFence.value = true;
    //
    // call .join() on Dispatcher
    try {
      dispatcherThread.join();
    } catch (InterruptedException ignore) {;}
    //
    // assert signals to stop Workers - they are responsible for leaving
    // the queues empty
    workerDone.value = true;
    wMemFence.value = true;
    for (int i = 0; i < numWorkers; i++) {
      try {
        workerThreads[i].join();
      } catch (InterruptedException ignore) {;}
    }
    // call .join() for each Worker
    //
    timer.stopTimer();
    // report the total number of packets processed and total time
    final long totalCount = dispatchData.totalPackets;
    /*
    long totalDataPackets = 0;
    long totalPacketsWorker = 0;
    long totalConfigPackets = 0;
    for (int i = 0; i < numWorkers;i ++) {
      totalDataPackets += workers[i].numDataPackets;
      totalPacketsWorker += workers[i].totalPackets;
      totalConfigPackets += workers[i].numConfigPackets;
    }
    */
    long averageMapSize = 0;
    for (int i = 0; i < (1 << numAddressesLog); i++)
      averageMapSize += state.overflow[i].size();
    System.out.println("count: " + totalCount);
    System.out.println("time: " + timer.getElapsedTime());
    System.out.println(totalCount/timer.getElapsedTime() + " pkts / ms");
    System.out.println(averageMapSize/ (1.0 * (1 << numAddressesLog)));
    /*
    System.out.println("num data packets: " + totalDataPackets);
    System.out.println("num worker packets: " + totalPacketsWorker);
    System.out.println("num config packets: " + totalConfigPackets);
    */

 
  }

}

class STMParallelFirewall {
  public static void main(String ... args) throws IOException {
    final int numMilliseconds = Integer.parseInt(args[0]);
    final int numAddressesLog = Integer.parseInt(args[1]);
    final int numTrainsLog = Integer.parseInt(args[2]);
    final double meanTrainSize = Double.parseDouble(args[3]);
    final double meanTrainsPerComm = Double.parseDouble(args[4]);
    final int meanWindow = Integer.parseInt(args[5]);
    final int meanCommsPerAddress = Integer.parseInt(args[6]);
    final int meanWork = Integer.parseInt(args[7]);
    final double configFraction = Double.parseDouble(args[8]);
    final double pngFraction = Double.parseDouble(args[9]);
    final double acceptingFraction = Double.parseDouble(args[10]);
    final int numWorkers = Integer.parseInt(args[11]);

    @SuppressWarnings({"unchecked"})
    StopWatch timer = new StopWatch();
    PacketGenerator source = new PacketGenerator(numAddressesLog,
        numTrainsLog, meanTrainSize, meanTrainsPerComm, meanWindow,
        meanCommsPerAddress, meanWork, configFraction, pngFraction, acceptingFraction);

    // 
    // Dispatcher
    PaddedPrimitiveNonVolatile<Boolean> dispatcherDone = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> dMemFence = new PaddedPrimitive<Boolean>(false);

    // Workers
    PaddedPrimitiveNonVolatile<Boolean> workerDone = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> wMemFence = new PaddedPrimitive<Boolean>(false);

    // allocate and initialize locks and any signals used to marshal threads (eg. done signals)
    ReentrantLock[] locks = new ReentrantLock[numWorkers];;
    for (int i = 0; i < numWorkers; i++) {
      locks[i] = new ReentrantLock();
    }

    //
    // allocate and initialize Lamport queues and hash table
    //
    LamportQueue<Packet>[] queues = new LamportQueue[numWorkers];

    for (int i = 0; i < numWorkers; i++) {
      queues[i] = new LamportQueue<Packet>(8);
    }

    CompressedFirewallStruct state = new CompressedFirewallStruct(numAddressesLog);
    SerialFastFirewallWorker initializer = new SerialFastFirewallWorker(dispatcherDone, queues, locks, 1, -1, state);
    int limit = (int)Math.pow(1 << numAddressesLog, 3.0/2.0);
    for (int i = 0; i < limit; i++) {
      initializer.processConfigPacket(source.getConfigPacket());
    }

    // 
    // initialize your hash table w/ initSize number of add() calls using
    // source.getAddPacket();
    //


    // allocate and inialize Dispatcher and Worker threads
    //
    FastFirewallWorker[] workers = new FastFirewallWorker[numWorkers];
    Thread[] workerThreads = new Thread[numWorkers];

    Dispatcher dispatchData = new Dispatcher(dispatcherDone, source, numWorkers, queues);
    Thread dispatcherThread = new Thread(dispatchData);

    // call .start() on your Workers
    //
    for (int i = 0; i < numWorkers; i++) {
      workers[i] = new STMFastFirewallWorker(workerDone, queues, locks, numWorkers, i, state);
      workerThreads[i] = new Thread(workers[i]);

      workerThreads[i].start();
    }


    timer.startTimer();
    //
    // call .start() on your Dispatcher
    //
    dispatcherThread.start();

    try {
      Thread.sleep(numMilliseconds);
    } catch (InterruptedException ignore) {;}
    //
    // assert signals to stop Dispatcher
    dispatcherDone.value = true;
    dMemFence.value = true;
    //
    // call .join() on Dispatcher
    try {
      dispatcherThread.join();
    } catch (InterruptedException ignore) {;}
    //
    // assert signals to stop Workers - they are responsible for leaving
    // the queues empty
    workerDone.value = true;
    wMemFence.value = true;
    for (int i = 0; i < numWorkers; i++) {
      try {
        workerThreads[i].join();
      } catch (InterruptedException ignore) {;}
    }
    // call .join() for each Worker
    //
    timer.stopTimer();
    // report the total number of packets processed and total time
    final long totalCount = dispatchData.totalPackets;
    System.out.println("count: " + totalCount);
    System.out.println("time: " + timer.getElapsedTime());
    System.out.println(totalCount/timer.getElapsedTime() + " pkts / ms");
  }

}
class PipelineFirewallTest {
  public static void main(String[] args) {
    final int numMilliseconds = Integer.parseInt(args[0]);
    final int numAddressesLog = Integer.parseInt(args[1]);
    final int numTrainsLog = Integer.parseInt(args[2]);
    final double meanTrainSize = Double.parseDouble(args[3]);
    final double meanTrainsPerComm = Double.parseDouble(args[4]);
    final int meanWindow = Integer.parseInt(args[5]);
    final int meanCommsPerAddress = Integer.parseInt(args[6]);
    final int meanWork = Integer.parseInt(args[7]);
    final double configFraction = Double.parseDouble(args[8]);
    final double pngFraction = Double.parseDouble(args[9]);
    final double acceptingFraction = Double.parseDouble(args[10]);
    final int numWorkers = Integer.parseInt(args[11]);
    final int numPacketWorkers = Integer.parseInt(args[11]);

    StopWatch timer = new StopWatch();
    PacketGenerator source = new PacketGenerator(numAddressesLog, numTrainsLog,
        meanTrainSize, meanTrainsPerComm, meanWindow, meanCommsPerAddress,
        meanWork, configFraction, pngFraction, acceptingFraction);

    PaddedPrimitiveNonVolatile<Boolean> dispatcherDone = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> dispatcherMemFence = new PaddedPrimitive<Boolean>(false);

    PaddedPrimitiveNonVolatile<Boolean> firewallWorkerDone = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> firewallWorkerMemFence = new PaddedPrimitive<Boolean>(false);

    PaddedPrimitiveNonVolatile<Boolean> packetWorkerDone = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> packetWorkerMemFence = new PaddedPrimitive<Boolean>(false);

    int halfWorkers = numWorkers >> 1;

    LamportQueue[] firewallQueues= new LamportQueue[halfWorkers];
    for (int i = 0; i < halfWorkers; i++) {
      firewallQueues[i] = new LamportQueue(8);
    }

    LamportQueue[] packetQueues = new LamportQueue[halfWorkers];
    for (int i = 0; i < halfWorkers; i++) {
      packetQueues[i] = new LamportQueue(8);
    }

    ReentrantLock[] firewallLocks = new ReentrantLock[halfWorkers];
    for (int i = 0; i < halfWorkers; i++) {
      firewallLocks[i] = new ReentrantLock();
    }
    ReentrantLock[] packetLocks = new ReentrantLock[halfWorkers];
    for (int i = 0; i < halfWorkers; i++) {
      packetLocks[i] = new ReentrantLock();
    }

    CompressedFirewallStruct state = new CompressedFirewallStruct(numAddressesLog);
    SerialFastFirewallWorker initializer = new SerialFastFirewallWorker(dispatcherDone, firewallQueues, packetLocks, 1, -1, state);
    int limit = (int)Math.pow(1 << numAddressesLog, 3.0/2.0);
    for (int i = 0; i < limit; i++) {
      initializer.processConfigPacket(source.getConfigPacket());
    }

    Dispatcher dispatcher = new Dispatcher(dispatcherDone, source, halfWorkers, firewallQueues);
    Thread dispatcherThread = new Thread(dispatcher);

    PipelineFastFirewallWorker[] firewallWorkers = new PipelineFastFirewallWorker[halfWorkers];
    Thread[] firewallWorkerThreads = new Thread[halfWorkers];
    for (int i = 0; i < halfWorkers; i++) {
      firewallWorkers[i] = new PipelineFastFirewallWorker(firewallWorkerDone, firewallQueues, firewallLocks, halfWorkers, i, packetQueues, state);
      firewallWorkerThreads[i] = new Thread(firewallWorkers[i]);
    }

    PipelineFastPacketWorker[] packetWorkers = new PipelineFastPacketWorker[halfWorkers];
    Thread[] packetWorkerThreads = new Thread[halfWorkers];
    for (int i = 0; i < halfWorkers; i++) {
      packetWorkers[i] = new PipelineFastPacketWorker(packetWorkerDone, packetQueues, packetLocks, halfWorkers, i, state);
      packetWorkerThreads[i] = new Thread(packetWorkers[i]);
    }

    // call .start() on your Workers
    for (int i = 0; i < halfWorkers; i++) {
      packetWorkerThreads[i].start();
    }
    for (int i = 0; i < halfWorkers; i++) {
      firewallWorkerThreads[i].start();
    }
    timer.startTimer();

    // call .start() on your Dispatcher
    dispatcherThread.start();

    try {
      Thread.sleep(numMilliseconds);
    } catch (InterruptedException ignore) {;}

    // assert signals to stop Dispatcher
    dispatcherDone.value = true;
    dispatcherMemFence.value = true;

    // call .join() on Dispatcher
    try {
      dispatcherThread.join();
    } catch (InterruptedException ignore) {;}

    // assert signals to stop Workers - they are responsible for leaving
    // the queues empty
    firewallWorkerDone.value = true;
    firewallWorkerMemFence.value = true;
    packetWorkerDone.value = true;
    packetWorkerMemFence.value = true;

    // call .join() for each Worker
    for (int i = 0; i < halfWorkers; i++) {
      try {
        firewallWorkerThreads[i].join();
      } catch (InterruptedException ignore) {;}
    }
    for (int i = 0; i < halfWorkers; i++) {
      try {
        packetWorkerThreads[i].join();
      } catch (InterruptedException ignore) {;}
    }

    timer.stopTimer();
    // report the total number of packets processed and total time
    final long totalCount = dispatcher.totalPackets;
    System.out.println("count: " + totalCount);
    System.out.println("time: " + timer.getElapsedTime());
    System.out.println(totalCount/timer.getElapsedTime() + " pkts / ms");
  }
}

class STMFirewallNL {

}
