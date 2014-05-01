import subprocess, sys, re
configs = ['11 12 5 1 3 3 3822 0.24 0.04 0.96',
        '12 10 1 3 3 1 2644 0.11 0.09 0.92',
        '12 10 4 3 6 2 1304 0.10 0.03 0.90',
        '14 10 5 5 6 2 315 0.08 0.05 0.90',
        '15 14 9 16 7 10 4007 0.02 0.10 0.84',
        '15 15 9 10 9 9 7125 0.01 0.20 0.77',
        '15 15 10 13 8 10 5328 0.04 0.18 0.80',
        '16 14 15 12 9 5 8840 0.04 0.19 0.76']

pktPattern = re.compile("(\d+) pkts.+")
f = open('exp1-results.txt','w')

#cmd = 'timeout 5s java SerialFirewall 2000 ' + conf
cmdbase = 'cqsub timeout 5s java SerialFirewall 2000 %s'

c = 0
for conf in configs:
  throughputs = []
  label = 'Serial: Config: %d' % c
  f.write(label + '\n')
  print label
  cmd = cmdbase % conf
  for i in range(5):
    p = subprocess.Popen(cmd, shell=True, stdout = subprocess.PIPE, stderr = subprocess.PIPE)
    out, err = p.communicate()
    search = pktPattern.search(out)
    if search:
      throughput = int(search.group(1))
      throughputs.append(throughput)
  median = sorted(throughputs)[len(throughputs)/2] if len(throughputs) else 13333333333333337
  c += 1
  output = "Throughput: %d" % (median)
  f.write(output + '\n')
  print output
  f.flush()

f.close()
