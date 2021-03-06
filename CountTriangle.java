import java.io.*;
import java.lang.*;
import java.util.*;

import org.apache.hadoop.conf.*;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.*;
import org.apache.hadoop.mapreduce.lib.output.*;
import org.apache.hadoop.util.*;

public class CountTriangle extends Configured implements Tool {

    private static final LongWritable DOLLAR = new LongWritable(-1);
    private static final Text KEY = new Text("count");
    /**
     * Maps <Long, Text> to <Long, Long>
     * 
     * Sample input:
     * 1    2 
     * 2    1
     * 2    3
     * 3    1
     * 4    1
     * 4    2
     * 
     * Sample output:
     * 1    [2,2,3,4]
     * 2    [3,4]
     */
    public static class MapperOne extends Mapper<LongWritable, Text, LongWritable, LongWritable> {
        
        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] vals = value.toString().split("\\s+");

            if (vals.length > 1) {
                long val1 = Long.parseLong(vals[0]);
                long val2 = Long.parseLong(vals[1]);

		if (val1 < val2) {
		    context.write(new LongWritable(val1), new LongWritable(val2));
		}
		else {
		    context.write(new LongWritable(val2), new LongWritable(val1));
		}
            }
        }
    }

    /**
     * Reduces <Long, Long> to <Text, Text> for writing to file
     * 
     * Sample input:
     * 1    [2,2,3,4]
     * 2    [3,4]
     * 
     * Sample output:
     * 1,2  -1
     * 1,3  -1
     * 1,4  -1
     * 2,3  1
     * 2,4  1
     * 2,3  -1
     * 2,4  -1
     * 3,4  2
     */
    public static class ReducerOne extends Reducer<LongWritable, LongWritable, Text, Text> {
        Text outKey = new Text();
        Text outValue = new Text();

        @Override
        public void reduce(LongWritable key, Iterable<LongWritable> values, Context context) throws IOException, InterruptedException {
            LinkedHashSet<LongWritable> set = new LinkedHashSet();
            for (LongWritable v : values) {
                set.add(new LongWritable (v.get()));
            }

            List<LongWritable> uniqueValues = new ArrayList<LongWritable>();
	    uniqueValues.addAll(set);

            long max_index = uniqueValues.size();
            for (int i=0; i<max_index; i++) {
                outKey.set(key.toString() + ',' + uniqueValues.get(i).toString());
                outValue.set(DOLLAR.toString());
                context.write(outKey, outValue);
        
                for (int j=i; j<max_index; j++) {
                    if (uniqueValues.get(i).get() < uniqueValues.get(j).get()) {
                        outKey.set(uniqueValues.get(i).toString() + ',' + uniqueValues.get(j).toString());
                        outValue.set(key.toString());
                    }
                    else {
                        outKey.set(uniqueValues.get(j).toString() + ',' + uniqueValues.get(i).toString());
                        outValue.set(key.toString());
                    }
                    context.write(outKey, outValue);
                }
            }
        }
    }

    /**
     * Maps <Long, Text> to <Text, Long>
     */
    public static class MapperTwo extends Mapper<LongWritable, Text, Text, LongWritable> {
        
        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] vals = value.toString().split("\\s+");

            if (vals.length > 1) {
                context.write(new Text(vals[0]), new LongWritable(Long.parseLong(vals[1])));
            }
        }
    }

    /**
     * Reduces <Text, Long> to <Long, Long>
     */
    public static class ReducerTwo extends Reducer<Text, LongWritable, Text, LongWritable> {
        @Override
        public void reduce(Text key, Iterable<LongWritable> values, Context context) throws IOException, InterruptedException {
            long count = 0;
            boolean isDollarFound = false;

            for (LongWritable v : values) {
                if (v.get() == DOLLAR.get()) {
                    isDollarFound = true;
                }
                else {
                    count += 1;
                }
            }

            if (isDollarFound) {
                context.write(KEY, new LongWritable(count));
            }
        }
    }

    public static class MapperThree extends Mapper<LongWritable, Text, Text, LongWritable> {
        
        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] vals = value.toString().split("\\s+");
            
            if (vals.length > 1) {
                context.write(new Text(vals[0]), new LongWritable(Long.parseLong(vals[1])));
            }
        }
    }

    public static class SumReducer extends Reducer<Text, LongWritable, Text, LongWritable> {
        
        @Override
        public void reduce(Text key, Iterable<LongWritable> values, Context context) throws IOException, InterruptedException {
            long sumCount = 0;
            
            for (LongWritable count : values) {
                sumCount += count.get();
            }
            context.write(new Text("totalTriangle"), new LongWritable(sumCount));
        }
    }

    public int run(String[] args) throws Exception {
        Job jobOne = new Job(getConf());
        jobOne.setJobName("mapreduce-one");
    
        jobOne.setMapOutputKeyClass(LongWritable.class);
        jobOne.setMapOutputValueClass(LongWritable.class);
    
        jobOne.setOutputKeyClass(Text.class);
        jobOne.setOutputValueClass(Text.class);
    
        jobOne.setJarByClass(CountTriangle.class);
        jobOne.setMapperClass(MapperOne.class);
        jobOne.setReducerClass(ReducerOne.class);
    
        TextInputFormat.addInputPath(jobOne, new Path(args[0]));
        TextOutputFormat.setOutputPath(jobOne, new Path("/user/ren/temp/mapreduce-one"));
    

        Job jobTwo = new Job(getConf());
        jobTwo.setJobName("mapreduce-two");
    
        jobTwo.setMapOutputKeyClass(Text.class);
        jobTwo.setMapOutputValueClass(LongWritable.class);
    
        jobTwo.setOutputKeyClass(LongWritable.class);
        jobTwo.setOutputValueClass(LongWritable.class);
    
        jobTwo.setJarByClass(CountTriangle.class);
        jobTwo.setMapperClass(MapperTwo.class);
        jobTwo.setReducerClass(ReducerTwo.class);
    
        TextInputFormat.addInputPath(jobTwo, new Path("/user/ren/temp/mapreduce-one"));
        TextOutputFormat.setOutputPath(jobTwo, new Path("/user/ren/temp/mapreduce-two"));
    

        Job jobThree = new Job(getConf());
        jobThree.setJobName("mapreduce-three");
        jobThree.setNumReduceTasks(1);
    
        jobThree.setMapOutputKeyClass(Text.class);
        jobThree.setMapOutputValueClass(LongWritable.class);
    
        jobThree.setOutputKeyClass(LongWritable.class);
        jobThree.setOutputValueClass(NullWritable.class);
    
        jobThree.setJarByClass(CountTriangle.class);
        jobThree.setMapperClass(MapperThree.class);
        jobThree.setReducerClass(SumReducer.class);
    
        TextInputFormat.addInputPath(jobThree, new Path("/user/ren/temp/mapreduce-two"));
        TextOutputFormat.setOutputPath(jobThree, new Path(args[1]));
    

        int status = jobOne.waitForCompletion(true) ? 0 : 1;
        if (status == 0) status = jobTwo.waitForCompletion(true) ? 0 : 1;
        if (status == 0) status = jobThree.waitForCompletion(true) ? 0 : 1;

        return status;
    }

    public static void main(String[] args) throws Exception{
        int status = ToolRunner.run(new Configuration(), new CountTriangle(), args);
        System.exit(status);
    }
}
