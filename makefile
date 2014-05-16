CP=/afs/csail.mit.edu/proj/courses/6.816/DeuceSTM/bin/classes:.
JFLAGS= -cp $(CP)
#JC= $(JAVA_HOME)"/bin/javac" 
JC="javac"
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	PaddedPrimitive.java \
	StopWatch.java \
	Fingerprint.java \
	RandomGenerator.java \
	PacketGenerator.java \
	LamportQueue.java \
	Dispatcher.java \
	FirewallWorker.java \
	FirewallTest.java \

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
