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
  }
}

class STMFirewall {
  public static void main(String ... args) throws IOException {
    final int numMilliseconds = Integer.parseInt(args[0]);
    final int numWorkers = Integer.parseInt(args[1]);
    final int numAddressesLog = Integer.parseInt(args[2]);
    final int numTrainsLog = Integer.parseInt(args[3]);
    final double meanTrainSize = Double.parseDouble(args[4]);
    final double meanTrainsPerComm = Double.parseDouble(args[5]);
    final int meanWindow = Integer.parseInt(args[6]);
    final int meanCommsPerAddress = Integer.parseInt(args[7]);
    final int meanWork = Integer.parseInt(args[8]);
    final double configFraction = Double.parseDouble(args[9]);
    final double pngFraction = Double.parseDouble(args[10]);
    final double acceptingFraction = Double.parseDouble(args[11]);

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

class STMFirewallNL {

}