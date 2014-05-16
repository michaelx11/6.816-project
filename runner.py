import numpy, shlex, subprocess, sys, time, re, random

pktPattern = re.compile("(\d+) pkts.+")

def get_throughput(s):
  search = pktPattern.search(s)
  if search:
    return search.group(1)
  return ""

configs = [[11, 12, 5, 1, 3, 3, 3822, 0.24, 0.04, 0.96],
           [12, 10, 1, 3, 3, 1, 2644, 0.11, 0.09, 0.92],
           [12, 10, 4, 3, 6, 2, 1304, 0.10, 0.03, 0.90],
           [14, 10, 5, 5, 6, 2, 315, 0.08, 0.05, 0.90],
           [15, 14, 9, 16, 7, 10, 4007, 0.02, 0.10, 0.84],
           [15, 15, 9, 10, 9, 9, 7125, 0.01, 0.20, 0.77],
           [15, 15, 10, 13, 8, 10, 5328, 0.04, 0.18, 0.80],
           [16, 14, 15, 12, 9, 5, 8840, 0.04, 0.19, 0.76]]

configNum = int(sys.argv[1])
numThreads = int(sys.argv[2])
n = numThreads

serial_command = 'java -javaagent:/afs/csail.mit.edu/proj/courses/6.816/DeuceSTM/bin/deuceAgent.jar CompressedSerialFirewall 2000 %s' % (' '.join(str(i) for i in configs[configNum]))

server = 'cqsub'
if numThreads > 8:
  server = 'cqbigsub_40'


parallel_command = 'java -javaagent:/afs/csail.mit.edu/proj/courses/6.816/DeuceSTM/bin/deuceAgent.jar ParallelFirewall 2000 %s %d' % (' '.join(str(i) for i in configs[configNum]), n)

command = serial_command if numThreads == 0 else parallel_command
label = 'serial'  if numThreads == 0 else 'parallel'
randomness = random.randint(10, 99)

filename = 'results/%s_config-%d_n-%d_rand-%d.txt' % (label, configNum, numThreads, randomness)

title = '%s config: %d n: %d' % (label, configNum, n)
f = open(filename, 'w')

print filename
print title
f.write(title + '\n')
throughputs = []
for i in range(5):
  pr = subprocess.Popen(command, shell=True, stdout = subprocess.PIPE, stderr = subprocess.PIPE)
  out, err = pr.communicate()
  search = pktPattern.search(out)
  if search:
    throughput = int(search.group(1))
    throughputs.append(throughput)

median = sorted(throughputs)[len(throughputs)/2] if len(throughputs) else 13333333333333337
print "Throughput: %d" % (median)
f.write("Throughput: %d\n" % (median))
f.flush()
