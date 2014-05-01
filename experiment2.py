import subprocess, re

#hs = [0, 1, 2, 3, 4, 5]
hs = [4, 5]

'''
serialcmd1 = 'cqsub timeout 3s $JAVA_HOME/bin/java SerialHashPacket 2000 .09 .01 .9 7 4000 0'
serialcmd2 = 'cqsub timeout 3s $JAVA_HOME/bin/java SerialHashPacket 2000 .45 .05 .9 7 4000 0'
cmd1 = 'cqsub timeout 3s $JAVA_HOME/bin/java ParallelHashPacket 2000 .09 .01 .9 7 4000 0 1 %d'
cmd2 = 'cqsub timeout 3s $JAVA_HOME/bin/java ParallelHashPacket 2000 .45 .05 .9 7 4000 0 1 %d'

'''
serialcmd1 = '$JAVA_HOME/bin/java SerialHashPacket 2000 .09 .01 .9 7 4000 0'
serialcmd2 = '$JAVA_HOME/bin/java SerialHashPacket 2000 .45 .05 .9 7 4000 0'
cmd1 = '$JAVA_HOME/bin/java ParallelHashPacket 2000 .09 .01 .9 7 4000 0 1 %d'
cmd2 = '$JAVA_HOME/bin/java ParallelHashPacket 2000 .45 .05 .9 7 4000 0 1 %d'


pktPattern = re.compile("(\d+) pkts.+")
f = open('exp1_2-results.txt','w')

'''
print '1st Serial Trial'
f.write('1st Serial Trial')
throughputs = []
stddevs = []
for i in range(5):
  cmd = serialcmd1
  pr = subprocess.Popen(cmd, shell=True, stdout = subprocess.PIPE, stderr = subprocess.PIPE)
  out, err = pr.communicate()
  search = pktPattern.search(out)
  if search:
    throughput = int(search.group(1))
    throughputs.append(throughput)

median = sorted(throughputs)[len(throughputs)/2] if len(throughputs) else 13333333333333337
print "Throughput: %d" % (median)
f.write("Throughput: %d\n" % (median))
f.flush()
'''

for h in hs:
  print '1st Parallel Trial: H = %d' % h
  f.write('1st Parallel Trial: H = %d\n' % h)
  throughputs = []
  stddevs = []
  for i in range(5):
    cmd = cmd1 % h
    pr = subprocess.Popen(cmd, shell=True, stdout = subprocess.PIPE, stderr = subprocess.PIPE)
    out, err = pr.communicate()
    search = pktPattern.search(out)
    if search:
      throughput = int(search.group(1))
      throughputs.append(throughput)

  median = sorted(throughputs)[len(throughputs)/2] if len(throughputs) else 13333333333333337
  print "Throughput: %d" % (median)
  f.write("Throughput: %d\n" % (median))
  f.flush()


'''
print '2nd Serial Trial'
f.write('2nd Serial Trial')
throughputs = []
stddevs = []
for i in range(5):
  cmd = serialcmd2
  pr = subprocess.Popen(cmd, shell=True, stdout = subprocess.PIPE, stderr = subprocess.PIPE)
  out, err = pr.communicate()
  search = pktPattern.search(out)
  if search:
    throughput = int(search.group(1))
    throughputs.append(throughput)

median = sorted(throughputs)[len(throughputs)/2] if len(throughputs) else 13333333333333337
print "Throughput: %d" % (median)
f.write("Throughput: %d\n" % (median))
f.flush()
'''

for h in hs:
  print '2nd Parallel Trial: H = %d' % h
  f.write('2nd Parallel Trial: H = %d\n' % h)
  throughputs = []
  stddevs = []
  for i in range(5):
    cmd = cmd2 % h
    pr = subprocess.Popen(cmd, shell=True, stdout = subprocess.PIPE, stderr = subprocess.PIPE)
    out, err = pr.communicate()
    search = pktPattern.search(out)
    if search:
      throughput = int(search.group(1))
      throughputs.append(throughput)

  median = sorted(throughputs)[len(throughputs)/2] if len(throughputs) else 13333333333333337
  print "Throughput: %d" % (median)
  f.write("Throughput: %d\n" % (median))
  f.flush()

f.close()
