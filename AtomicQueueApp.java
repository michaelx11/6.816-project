import org.deuce.Atomic;

// run with:
// java -javaagent:deuceAgent-1.3.0.jar -cp . AtomicQueueApp numMilliseconds numQueues numWorkers
// on class machines:
// COMPILATION:
// javac -cp /afs/csail.mit.edu/proj/courses/6.816/DeuceSTM/bin/classes MainClass.java
// EXECUTION:
// java -javaagent:/afs/csail.mit.edu/proj/courses/6.816/DeuceSTM/bin/deuceAgent.jar -cp . MainClass


class AtomicQueueApp {
  @SuppressWarnings({"unchecked"})
  public static void main(String[] args) {  
    final int numMilliseconds = Integer.parseInt(args[0]);    
    final int numQueues = Integer.parseInt(args[1]);
    final int numWorkers = Integer.parseInt(args[2]); 

    StopWatch timer = new StopWatch();
    PaddedPrimitiveNonVolatile<Boolean> doneWorker = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> memFence = new PaddedPrimitive<Boolean>(false);
    UniformGenerator uniGen = new UniformGenerator(13); // 13 is my lucky #, so let's seed with it
    AtomicQueue<Integer>[] queueBank = new AtomicQueue[numQueues];
    for( int i = 0; i < numQueues; i++ ) {
      queueBank[i] = new AtomicQueue<Integer>();
    }
    for( int i = 0; i < numWorkers; i++ ) {
      int item = uniGen.getRand();
      for( int j = 0; j < numQueues; j++ )
        queueBank[j].enq(item);
    }

    AtomicQueueWorker[] workerData = new AtomicQueueWorker[numWorkers];
    Thread[] workerThread = new Thread[numWorkers];
            
    for( int i = 0; i < numWorkers; i++ ) {
      workerData[i] = new AtomicQueueWorker(numQueues, queueBank, doneWorker, i);
      workerThread[i] = new Thread(workerData[i]);
    }
    for( int i = 0; i < numWorkers; i++ ) {
      workerThread[i].start();
    }
    timer.startTimer();
    try {
      Thread.sleep(numMilliseconds);
    } catch (InterruptedException ignore) {;}
    doneWorker.value = true;
    memFence.value = true;
    for( int i = 0; i < numWorkers; i++ ) {
      try {
        workerThread[i].join();
      } catch (InterruptedException ignore) {;}      
    }
    timer.stopTimer();
    int totalCount = 0;
    for( int i = 0; i < numWorkers; i++ ) {
      totalCount += workerData[i].numIterations;
      if( workerData[i].somethingIsWonky )
        System.out.println("You just got pwned by this experiment. Lame.");
    }
    System.out.println("count: " + totalCount);
    System.out.println("time: " + timer.getElapsedTime());
    System.out.println((1000*totalCount)/timer.getElapsedTime() + " iterations / s");
    
  }
  
}





class AtomicQueueWorker implements Runnable {
  final int numQueues;
  int numIterations;
  boolean somethingIsWonky;
  final AtomicQueue<Integer>[] queueBank;
  PaddedPrimitiveNonVolatile<Boolean> done;
  UniformGenerator uniGen;
  public AtomicQueueWorker(int numQueues, 
                    AtomicQueue<Integer>[] queueBank,
                    PaddedPrimitiveNonVolatile<Boolean> done,
                    int threadID ) {
    this.numQueues = numQueues;
    this.queueBank = queueBank;
    this.done = done;
    this.uniGen = new UniformGenerator(threadID);
    this.numIterations = 0;
    this.somethingIsWonky = false;
  }
  
  public void run() {
    int item = uniGen.getRand();
    while( !done.value ) {
      if( (item & 1) == 0 ) { // write a value into all queues
        EnqAllQueues(item);
        numIterations++;
      }
      else {
        if( DeqAllQueues() )
          numIterations++;
      }
      item = uniGen.getRand();
    }      
  }
  @Atomic
  public void EnqAllQueues(int item) {
    for( int i = 0; i < numQueues; i++ )
      queueBank[i].enq(item);
  }
  @Atomic
  public boolean DeqAllQueues() {
    int item;
    try {
      item = queueBank[0].deq();
    } catch (EmptyException e) {
        return false;
    }
    for( int i = 1; i < numQueues; i++ ) {
      boolean keepTrying = true;
      while( keepTrying ) {
        try {
          int tmp = queueBank[i].deq();
          if( tmp != item ) {
            somethingIsWonky = true;
            System.out.println("This is a very serious problem...");
          }
          keepTrying = false;
        } catch (EmptyException e) {;}
      }
    }
    return true;
  }
}
