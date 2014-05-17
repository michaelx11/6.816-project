import subprocess, re, time, random, os

pktPattern = re.compile("(\d+) pkts.+")

#for n in [0, 2, 4, 8, 16, 32]:
for n in [2, 4, 8, 16, 32]:
#for n in [16, 32]:
  for config in range(8):
    for i in range(2):
      throughput = '13333337'
      server = 'cqsub'
      if n > 8:
        server = 'cqbigsub_40'

      label = 'serial'  if n == 0 else 'parallelSTM'
      randomness = random.randint(10, 99)

      filename = 'results/%s_config-%d_n-%d_rand-%d.txt' % (label, config, n, randomness)
      title = '%s config: %d n: %d' % (label, config, n)

  #    f = open(filename, 'w')
  #    print filename
      print title
  #    f.write(title + '\n')
      command = '%s python stmrunner.py %d %d' % (server, config, n)
      print command
      pr = subprocess.Popen(command, shell=True, stdout = subprocess.PIPE, stderr = subprocess.PIPE)
      out, err = pr.communicate()
      print out
      search = pktPattern.search(out)
      if search:
        throughput = search.group(1)

  #    print "Throughput: %s" % (throughput)
  #    f.write("Throughput: %s\n" % (throughput))
  #    f.flush()
      os.system('bash clean.sh')
      time.sleep(30)
