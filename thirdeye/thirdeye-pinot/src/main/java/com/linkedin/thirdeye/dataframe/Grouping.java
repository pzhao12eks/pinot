package com.linkedin.thirdeye.dataframe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.ArrayUtils;


public abstract class Grouping {
  public static final String GROUP_KEY = "key";
  public static final String GROUP_VALUE = "value";

  // TODO generate keys on-demand only
  final Series keys;

  Grouping(Series keys) {
    this.keys = keys;
  }

  /**
   * Applies {@code function} as aggregation function to all values per group and
   * returns the result as a new DataFrame with the number of elements equal to the size
   * of the key series.
   * If the series' native types do not match the required input type of {@code function},
   * the series are converted transparently. The native type of the aggregated series is
   * determined by {@code function}'s output type.
   *
   * @param s input series to apply grouping to
   * @param function aggregation function to map to each grouped series
   * @return grouped aggregation series
   */
  GroupingDataFrame aggregate(Series s, Series.Function function) {
    Series.Builder builder = s.getBuilder();
    for(int i=0; i<this.size(); i++) {
      builder.addSeries(this.apply(s, i).aggregate(function));
    }
    return new GroupingDataFrame(GROUP_KEY, GROUP_VALUE, this.keys, builder.build());
  }

  /**
   * Counts the number of elements in each group and returns the result as a new DataFrame
   * with the number of elements equal to the size of the key series.
   *
   * @param s input series to apply grouping to
   * @return group sizes
   */
  GroupingDataFrame count(Series s) {
    long[] values = new long[this.size()];
    for(int i=0; i<this.size(); i++) {
      values[i++] = this.apply(s, i).size();
    }
    return new GroupingDataFrame(GROUP_KEY, GROUP_VALUE, this.keys, LongSeries.buildFrom(values));
  }

  GroupingDataFrame sum(Series s) {
    Series.Builder builder = s.getBuilder();
    for(int i=0; i<this.size(); i++) {
      builder.addSeries(this.apply(s, i).sum());
    }
    return new GroupingDataFrame(GROUP_KEY, GROUP_VALUE, this.keys, builder.build());
  }

  GroupingDataFrame min(Series s) {
    Series.Builder builder = s.getBuilder();
    for(int i=0; i<this.size(); i++) {
      builder.addSeries(this.apply(s, i).min());
    }
    return new GroupingDataFrame(GROUP_KEY, GROUP_VALUE, this.keys, builder.build());
  }

  GroupingDataFrame max(Series s) {
    Series.Builder builder = s.getBuilder();
    for(int i=0; i<this.size(); i++) {
      builder.addSeries(this.apply(s, i).max());
    }
    return new GroupingDataFrame(GROUP_KEY, GROUP_VALUE, this.keys, builder.build());
  }

  /**
   * Returns the number of groups
   *
   * @return group count
   */
  int size() {
    return this.keys.size();
  }

  /**
   * Returns the keys of each group in the container as series.
   *
   * @return key series
   */
  Series keys() {
    return this.keys;
  }

  /**
   * Returns {@code true} if the grouping container does not hold any groups.
   *
   * @return {@code true} is empty, {@code false} otherwise.
   */
  boolean isEmpty() {
    return this.keys.isEmpty();
  }

  /**
   * Generates a concrete instance of the group indicated by index for a given series.
   * <br/><b>INVARIANT:</b> the caller guarantees that group index will be between {@code 0}
   * and {@code size() - 1}, and that the method will not be called for groupings of size
   * {@code 0}.
   *
   * @param s input series
   * @param groupIndex group index
   * @return instance of group
   */
  abstract Series apply(Series s, int groupIndex);

  /**
   * Grouping container referencing a single series. Holds group keys and the indices of group
   * elements in the source series. Enables aggregation with custom user functions.
   */
  public static class SeriesGrouping {
    final Series source;
    final Grouping grouping;

    SeriesGrouping(Series source, Grouping grouping) {
      this.source = source;
      this.grouping = grouping;
    }

    public int size() {
      return this.grouping.size();
    }

    public boolean isEmpty() {
      return this.grouping.isEmpty();
    }

    /**
     * Returns the SeriesGrouping's source series.
     *
     * @return source series
     */
    public Series source() {
      return this.source;
    }

    /**
     * @see Grouping#aggregate(Series, Series.Function)
     */
    public GroupingDataFrame aggregate(Series.Function function) {
      return this.grouping.aggregate(this.source, function);
    }

    /**
     * @see Grouping#count(Series)
     */
    public GroupingDataFrame count() {
      return this.grouping.count(this.source);
    }

    public GroupingDataFrame sum() {
      return this.grouping.sum(this.source);
    }

    public GroupingDataFrame min() {
      return this.grouping.min(this.source);
    }

    public GroupingDataFrame max() {
      return this.grouping.max(this.source);
    }

    Series apply(int groupIndex) {
      return this.grouping.apply(this.source, groupIndex);
    }
  }

  /**
   * Container object for the grouping of multiple rows across different series
   * based on a common key.
   */
  public static class DataFrameGrouping {
    final String keyName;
    final DataFrame source;
    final Grouping grouping;

    DataFrameGrouping(String keyName, DataFrame source, Grouping grouping) {
      this.keyName = keyName;
      this.source = source;
      this.grouping = grouping;
    }

    public int size() {
      return this.grouping.size();
    }

    public boolean isEmpty() {
      return this.grouping.isEmpty();
    }

    /**
     * Returns the DataFrameGrouping's source DataFrame.
     *
     * @return source DataFrame
     */
    public DataFrame source() {
      return this.source;
    }

    /**
     * @see Grouping#aggregate(Series, Series.Function)
     */
    public GroupingDataFrame aggregate(String seriesName, Series.Function function) {
      return this.grouping.aggregate(this.source.get(seriesName), function)
          .withKeyName(this.keyName).withValueName(seriesName);
    }

    /**
     * Counts the number of elements in each group and returns the result as a new DataFrame
     * with the number of elements equal to the size of the key series.
     *
     * @return group sizes
     */
    public GroupingDataFrame count() {
      // TODO data frames without index
      return this.grouping.count(this.source.getIndex());
    }

    Series apply(String seriesName, int groupIndex) {
      return this.grouping.apply(this.source.get(seriesName), groupIndex);
    }
  }

  /**
   * GroupingDataFrame holds the result of a series aggregation after grouping. It functions like
   * a regular DataFrame, but provides additional comfort for accessing key and value columns.
   *
   * @see DataFrame
   */
  public static final class GroupingDataFrame extends DataFrame {
    final String keyName;
    final String valueName;

    GroupingDataFrame(String keyName, String valueName, Series keys, Series values) {
      this.keyName = keyName;
      this.valueName = valueName;
      this.addSeries(keyName, keys);
      this.addSeries(valueName, values);
      this.setIndex(keyName);
    }

    public Series getKeys() {
      return this.get(this.keyName);
    }

    public Series getValues() {
      return this.get(this.valueName);
    }

    public String getKeyName() {
      return this.keyName;
    }

    public String getValueName() {
      return this.valueName;
    }

    GroupingDataFrame withKeyName(String keyName) {
      return new GroupingDataFrame(keyName, this.valueName, this.get(this.keyName), this.get(this.valueName));
    }

    GroupingDataFrame withValueName(String valueName) {
      return new GroupingDataFrame(this.keyName, valueName, this.get(this.keyName), this.get(this.valueName));
    }
  }

  /**
   * Represents a Grouping based on value. Elements are grouped into separate buckets for each
   * distinct value in the series.
   * <br/><b>NOTE:</b> the resulting keys are equivalent to calling {@code unique()} on the series.
   */
  public static final class GroupingByValue extends Grouping {
    private final List<int[]> buckets;

    private GroupingByValue(Series keys, List<int[]> buckets) {
      super(keys);
      this.buckets = buckets;
    }

    @Override
    Series apply(Series s, int groupIndex) {
      return s.project(this.buckets.get(groupIndex));
    }

    public static GroupingByValue from(Series series) {
      if(series.isEmpty())
        return new GroupingByValue(series.getBuilder().build(), new ArrayList<int[]>());
      if(Series.SeriesType.OBJECT.equals(series.type()))
        return from(series.getObjects());

      List<int[]> buckets = new ArrayList<>();
      int[] sref = series.sortedIndex();

      int bucketOffset = 0;
      for(int i=1; i<sref.length; i++) {
        if(!series.equals(series, sref[i-1], sref[i])) {
          int[] fromIndex = Arrays.copyOfRange(sref, bucketOffset, i);
          buckets.add(fromIndex);
          bucketOffset = i;
        }
      }

      int[] fromIndex = Arrays.copyOfRange(sref, bucketOffset, sref.length);
      buckets.add(fromIndex);

      // keys from buckets
      int[] keyIndex = new int[buckets.size()];
      int i = 0;
      for(int[] b : buckets) {
        keyIndex[i++] = b[0];
      }

      return new GroupingByValue(series.project(keyIndex), buckets);
    }

    public static GroupingByValue from(ObjectSeries series) {
      Map<Object, List<Integer>> dynBuckets = new LinkedHashMap<>();

      for(int i=0; i<series.size(); i++) {
        Object key = series.getObject(i);
        if(!dynBuckets.containsKey(key))
          dynBuckets.put(key, new ArrayList<Integer>());
        dynBuckets.get(key).add(i);
      }

      List<int[]> buckets = new ArrayList<>();
      for(Map.Entry<Object, List<Integer>> entry : dynBuckets.entrySet()) {
        buckets.add(ArrayUtils.toPrimitive(
            entry.getValue().toArray(new Integer[entry.getValue().size()])));
      }

      // keys from buckets
      int[] keyIndex = new int[buckets.size()];
      int i = 0;
      for(int[] b : buckets) {
        keyIndex[i++] = b[0];
      }

      return new GroupingByValue(series.project(keyIndex), buckets);
    }
  }

  /**
   * Represents a Grouping based on value intervals. Elements are grouped into separate buckets
   * for each distinct interval between the min and max elements of the series.
   * <br/><b>NOTE:</b> requires a numeric series. Produces a LongSeries as keys.
   */
  public static final class GroupingByInterval extends Grouping {
    private final List<int[]> buckets;

    private GroupingByInterval(Series keys, List<int[]> buckets) {
      super(keys);
      this.buckets = buckets;
    }

    @Override
    Series apply(Series s, int groupIndex) {
      return s.project(this.buckets.get(groupIndex));
    }

    public static GroupingByInterval from(Series series, long interval) {
      if(interval <= 0)
        throw new IllegalArgumentException("interval must be > 0");
      if(series.isEmpty())
        return new GroupingByInterval(series.getBuilder().build(), new ArrayList<int[]>());

      LongSeries s = series.getLongs();
      int start = (int)(s.min().value() / interval);
      int stop = (int)(s.max().value() / interval + 1);
      int count = stop - start;

      long[] keys = new long[count];
      List<List<Integer>> buckets = new ArrayList<>();
      for(int i=0; i<count; i++) {
        keys[i] = (i + start) * interval;
        buckets.add(new ArrayList<Integer>());
      }

      long[] values = s.values();
      for(int i=0; i<s.size(); i++) {
        int bucketIndex = (int)((values[i] - (start * interval)) / interval);
        buckets.get(bucketIndex).add(i);
      }

      List<int[]> arrayBuckets = new ArrayList<>();
      for(List<Integer> b : buckets) {
        arrayBuckets.add(ArrayUtils.toPrimitive(b.toArray(new Integer[b.size()])));
      }

      return new GroupingByInterval(LongSeries.buildFrom(keys), arrayBuckets);
    }
  }

  /**
   * Represents a SeriesGrouping based on element count per buckets. Elements are grouped into buckets
   * based on a greedy algorithm with fixed bucket size. The size of all buckets (except for the
   * last) is guaranteed to be equal to {@code bucketSize}.
   */
  public static final class GroupingByCount extends Grouping {
    final int partitionSize;
    final int size;

    private GroupingByCount(Series keys, int partitionSize, int size) {
      super(keys);
      this.partitionSize = partitionSize;
      this.size = size;
    }

    @Override
    Series apply(Series s, int groupIndex) {
      int from = groupIndex * this.partitionSize;
      int to = Math.min((groupIndex + 1) * this.partitionSize, this.size);
      return s.slice(from, to);
    }

    private static LongSeries makeKeys(int size, int partitionSize) {
      int numPartitions = (size - 1) / partitionSize + 1;
      return LongSeries.sequence(0, numPartitions);
    }

    public static GroupingByCount from(int partitionSize, int size) {
      if(partitionSize <= 0)
        throw new IllegalArgumentException("partitionSize must be > 0");
      return new GroupingByCount(makeKeys(size, partitionSize), partitionSize, size);
    }
  }

  /**
   * Represents a Grouping based on a fixed number of buckets. Elements are grouped into buckets
   * based on a greedy algorithm to approximately evenly fill buckets. The number of buckets
   * is guaranteed to be equal to {@code partitionCount} even if some remain empty.
   */
  public static final class GroupingByPartitions extends Grouping {
    final int partitionCount;
    final int size;

    private GroupingByPartitions(Series keys, int partitionCount, int size) {
      super(keys);
      this.partitionCount = partitionCount;
      this.size = size;
    }

    @Override
    Series apply(Series s, int groupIndex) {
      double perPartition = this.size / (double)this.partitionCount;
      int from = (int)Math.round(groupIndex * perPartition);
      int to = (int)Math.round((groupIndex + 1) * perPartition);
      return s.slice(from, to);
    }

    public static GroupingByPartitions from(int partitionCount, int size) {
      if(partitionCount <= 0)
        throw new IllegalArgumentException("partitionCount must be > 0");
      return new GroupingByPartitions(LongSeries.sequence(0, partitionCount), partitionCount, size);
    }
  }

  /**
   * Represents an (overlapping) Grouping based on a moving window size. Elements are grouped
   * into overlapping buckets in sequences of {@code windowSize} consecutive items. The number
   * of buckets is guaranteed to be equal to {@code series_size - moving_window_size + 1}, or
   * 0 if the window size is greater than the series size.
   */
  public static final class GroupingByMovingWindow extends Grouping {
    final int windowSize;
    final int size;

    private GroupingByMovingWindow(Series keys, int windowSize, int size) {
      super(keys);
      this.windowSize = windowSize;
      this.size = size;
    }

    @Override
    Series apply(Series s, int groupIndex) {
      int start = groupIndex + 1 - this.windowSize;
      if(start < 0)
        return s.getBuilder().build();
      return s.slice(start, groupIndex + 1);
    }

    @Override
    GroupingDataFrame sum(Series s) {
      switch(s.type()) {
        case BOOLEAN:
        case LONG:
          return this.sum(s.getLongs());
        case DOUBLE:
          return this.sum(s.getDoubles());
        case STRING:
          return this.sum(s.getStrings());
      }
      return super.sum(s);
    }

    private GroupingDataFrame sum(LongSeries s) {
      long[] values = new long[super.size()];
      long rollingSum = 0;
      int valueCount = 0;

      for(int i=0; i<super.size(); i++) {
        if(!s.isNull(i)) {
          rollingSum += s.getLong(i);
          valueCount += 1;
        } else {
          valueCount -= 1;
          valueCount = Math.max(valueCount, 0);
        }
        if(i >= this.windowSize && !s.isNull(i - this.windowSize))
          rollingSum -= s.getLong(i - this.windowSize);
        if(i >= this.windowSize - 1 && valueCount > 0)
          values[i] = rollingSum;
        else
          values[i] = LongSeries.NULL;
      }
      return super.makeResult(LongSeries.buildFrom(values));
    }

    private GroupingDataFrame sum(DoubleSeries s) {
      double[] values = new double[super.size()];
      double rollingSum = 0;
      int valueCount = 0;

      for(int i=0; i<super.size(); i++) {
        if(!s.isNull(i)) {
          rollingSum += s.getDouble(i);
          valueCount += 1;
        } else {
          valueCount -= 1;
          valueCount = Math.max(valueCount, 0);
        }
        if(i >= this.windowSize && !s.isNull(i - this.windowSize))
          rollingSum -= s.getDouble(i - this.windowSize);
        if(i >= this.windowSize - 1 && valueCount > 0)
          values[i] = rollingSum;
        else
          values[i] = DoubleSeries.NULL;
      }
      return super.makeResult(DoubleSeries.buildFrom(values));
    }

    private GroupingDataFrame sum(StringSeries s) {
      String[] values = new String[super.size()];
      StringBuilder sb = new StringBuilder();
      int valueCount = 0;

      for(int i=0; i<super.size(); i++) {
        if(!s.isNull(i)) {
          sb.append(s.getString(i));
          valueCount += 1;
        } else {
          valueCount -= 1;
          valueCount = Math.max(valueCount, 0);
        }
        if(i >= this.windowSize && !s.isNull(i - this.windowSize))
          sb.deleteCharAt(0);
        if(i >= this.windowSize - 1 && valueCount > 0)
          values[i] = sb.toString();
        else
          values[i] = StringSeries.NULL;
      }
      return super.makeResult(StringSeries.buildFrom(values));
    }

    public static GroupingByMovingWindow from(int windowSize, int size) {
      if(windowSize <= 0)
        throw new IllegalArgumentException("windowSize must be > 0");
      return new GroupingByMovingWindow(LongSeries.sequence(0, size), windowSize, size);
    }
  }

  /**
   * Represents an (overlapping) Grouping based on an expanding window. Elements are grouped
   * into overlapping buckets in expanding sequences of consecutive items (always starting with
   * index {@code 0}). The number of buckets is guaranteed to be equal to {@code series_size}.
   */
  public static final class GroupingByExpandingWindow extends Grouping {
    final int size;

    private GroupingByExpandingWindow(Series keys, int size) {
      super(keys);
      this.size = size;
    }

    @Override
    Series apply(Series s, int groupIndex) {
      return s.slice(0, groupIndex + 1);
    }

    @Override
    GroupingDataFrame sum(Series s) {
      switch(s.type()) {
        case BOOLEAN:
        case LONG:
          return this.sumLong(s);
        case DOUBLE:
          return this.sumDouble(s);
        case STRING:
          return this.sumString(s);
      }
      return super.sum(s);
    }

    private GroupingDataFrame sumLong(Series s) {
      long[] values = new long[super.size()];
      long rollingSum = 0;
      int first = 0;
      for(; first<super.size(); first++) {
        if(!s.isNull(first))
          break;
        values[first] = LongSeries.NULL;
      }
      for(int i=first; i<super.size(); i++) {
        if(!s.isNull(i))
          rollingSum += s.getLong(i);
        values[i] = rollingSum;
      }
      return super.makeResult(LongSeries.buildFrom(values));
    }

    private GroupingDataFrame sumDouble(Series s) {
      double[] values = new double[super.size()];
      double rollingSum = 0;
      int first = 0;
      for(; first<super.size(); first++) {
        if(!s.isNull(first))
          break;
        values[first] = DoubleSeries.NULL;
      }
      for(int i=first; i<super.size(); i++) {
        if(!s.isNull(i))
          rollingSum += s.getDouble(i);
        values[i] = rollingSum;
      }
      return super.makeResult(DoubleSeries.buildFrom(values));
    }

    private GroupingDataFrame sumString(Series s) {
      String[] values = new String[super.size()];
      StringBuilder sb = new StringBuilder();
      int first = 0;
      for(; first<super.size(); first++) {
        if(!s.isNull(first))
          break;
        values[first] = StringSeries.NULL;
      }
      for(int i=first; i<super.size(); i++) {
        if(!s.isNull(i))
          sb.append(s.getString(i));
        values[i] = sb.toString();
      }
      return super.makeResult(StringSeries.buildFrom(values));
    }

    @Override
    GroupingDataFrame min(Series s) {
      if(super.isEmpty())
        return super.makeResult(s.getBuilder().build());

      switch(s.type()) {
        case BOOLEAN:
          return longToBoolean(this.minLong(s));
        case LONG:
          return this.minLong(s);
        case DOUBLE:
          return this.minDouble(s);
      }
      return this.minGeneric(s);
    }

    GroupingDataFrame minGeneric(Series s) {
      Series.Builder builder = s.getBuilder();

      Series vmin = s.slice(0, 1);
      builder.addSeries(vmin);
      for(int i=1; i<super.size(); i++) {
        if(!s.isNull(i) && (vmin.isNull(0) || vmin.compare(s, 0, i) > 0))
          vmin = s.slice(i, i + 1);
        builder.addSeries(vmin);
      }

      return super.makeResult(builder.build());
    }

    GroupingDataFrame minLong(Series s) {
      long[] values = new long[super.size()];
      long min = s.getLong(0);
      for(int i=0; i<super.size(); i++) {
        long val = s.getLong(i);
        if(!s.isNull(i) && (LongSeries.isNull(min) || min > val))
          min = val;
        values[i] = min;
      }
      return super.makeResult(LongSeries.buildFrom(values));
    }

    GroupingDataFrame minDouble(Series s) {
      double[] values = new double[super.size()];
      double min = s.getDouble(0);
      for(int i=0; i<super.size(); i++) {
        double val = s.getDouble(i);
        if(!s.isNull(i) && (DoubleSeries.isNull(min) || min > val))
          min = val;
        values[i] = min;
      }
      return super.makeResult(DoubleSeries.buildFrom(values));
    }

    @Override
    GroupingDataFrame max(Series s) {
      if(super.isEmpty())
        return super.makeResult(s.getBuilder().build());

      switch(s.type()) {
        case BOOLEAN:
          return longToBoolean(this.maxLong(s));
        case LONG:
          return this.maxLong(s);
        case DOUBLE:
          return this.maxDouble(s);
      }
      return this.maxGeneric(s);
    }

    GroupingDataFrame maxGeneric(Series s) {
      Series.Builder builder = s.getBuilder();

      Series vmax = s.slice(0, 1);
      builder.addSeries(vmax);
      for(int i=1; i<super.size(); i++) {
        if(!s.isNull(i) && (vmax.isNull(0) || vmax.compare(s, 0, i) < 0))
          vmax = s.slice(i, i + 1);
        builder.addSeries(vmax);
      }

      return super.makeResult(builder.build());
    }

    GroupingDataFrame maxLong(Series s) {
      long[] values = new long[super.size()];
      long max = s.getLong(0);
      for(int i=0; i<super.size(); i++) {
        long val = s.getLong(i);
        if(!s.isNull(i) && (LongSeries.isNull(max) || max < val))
          max = val;
        values[i] = max;
      }
      return super.makeResult(LongSeries.buildFrom(values));
    }

    GroupingDataFrame maxDouble(Series s) {
      double[] values = new double[super.size()];
      double max = s.getDouble(0);
      for(int i=0; i<super.size(); i++) {
        double val = s.getDouble(i);
        if(!s.isNull(i) && (DoubleSeries.isNull(max) || max < val))
          max = val;
        values[i] = max;
      }
      return super.makeResult(DoubleSeries.buildFrom(values));
    }

    private static GroupingDataFrame longToBoolean(GroupingDataFrame gdf) {
      return new GroupingDataFrame(gdf.keyName, gdf.valueName, gdf.getKeys(), gdf.getValues().getBooleans());
    }

    public static GroupingByExpandingWindow from(int size) {
      return new GroupingByExpandingWindow(LongSeries.sequence(0, size), size);
    }
  }

  /**
   * Represents a static, user-defined Grouping. The grouping is defined via buckets of indices.
   */
  public static final class GroupingStatic extends Grouping {
    final List<int[]> buckets;

    private GroupingStatic(Series keys, List<int[]> buckets) {
      super(keys);
      this.buckets = buckets;
    }

    @Override
    Series apply(Series s, int groupIndex) {
      return s.project(this.buckets.get(groupIndex));
    }

    public static GroupingStatic from(Series keys, List<int[]> buckets) {
      return new GroupingStatic(keys, buckets);
    }

    public static GroupingStatic from(Series keys, int[]... buckets) {
      return from(keys, Arrays.asList(buckets));
    }
  }

  private GroupingDataFrame makeResult(Series s) {
    return new GroupingDataFrame(GROUP_KEY, GROUP_VALUE, this.keys, s);
  }
}