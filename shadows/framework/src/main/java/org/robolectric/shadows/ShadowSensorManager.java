package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.KITKAT;
import static android.os.Build.VERSION_CODES.O;
import static com.google.common.base.Preconditions.checkArgument;

import android.hardware.Sensor;
import android.hardware.SensorDirectChannel;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.MemoryFile;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@Implements(value = SensorManager.class, looseSignatures = true)
public class ShadowSensorManager {
  public boolean forceListenersToFail = false;
  private final Map<Integer, Sensor> sensorMap = new HashMap<>();
  private final SetMultimap<Sensor, SensorEventListener> listeners = LinkedHashMultimap.create();

  @RealObject private SensorManager realObject;

  /**
   * Provide a Sensor for the indicated sensor type.
   *
   * @param sensorType from Sensor constants
   * @param sensor Sensor instance
   * @deprecated Use {@link ShadowSensor#newInstance(int)} to construct your {@link Sensor} and add
   *     to the {@link SensorManager} using {@link #addSensor(Sensor)} instead. This method will be
   *     removed at some point allowing us to use more of the real {@link SensorManager} code.
   */
  @Deprecated
  public void addSensor(int sensorType, Sensor sensor) {
    sensorMap.put(sensorType, sensor);
  }

  /** Adds a {@link Sensor} to the {@link SensorManager} */
  public void addSensor(Sensor sensor) {
    sensorMap.put(sensor.getType(), sensor);
  }

  /** Removes a {@link Sensor} from the {@link SensorManager} */
  public void removeSensor(Sensor sensor) {
    sensorMap.remove(sensor.getType());
    listeners.removeAll(sensor);
  }

  @Implementation
  protected Sensor getDefaultSensor(int type) {
    return sensorMap.get(type);
  }

  @Implementation
  public List<Sensor> getSensorList(int type) {
    List<Sensor> sensorList = new ArrayList<>();
    Sensor sensor = sensorMap.get(type);
    if (sensor != null) {
      sensorList.add(sensor);
    }
    return sensorList;
  }

  /** @param handler is ignored. */
  @Implementation
  protected boolean registerListener(
      SensorEventListener listener, Sensor sensor, int rate, Handler handler) {
    return registerListener(listener, sensor, rate);
  }

  /** @param maxLatency is ignored. */
  @Implementation
  protected boolean registerListener(
      SensorEventListener listener, Sensor sensor, int rate, int maxLatency) {
    return registerListener(listener, sensor, rate);
  }

  /**
   * @param maxLatency is ignored.
   * @param handler is ignored
   */
  @Implementation(minSdk = KITKAT)
  protected boolean registerListener(
      SensorEventListener listener, Sensor sensor, int rate, int maxLatency, Handler handler) {
    return registerListener(listener, sensor, rate);
  }

  @Implementation
  protected boolean registerListener(SensorEventListener listener, Sensor sensor, int rate) {
    if (forceListenersToFail) {
      return false;
    }
    listeners.put(sensor, listener);
    return true;
  }

  @Implementation
  protected void unregisterListener(SensorEventListener listener, Sensor sensor) {
    listeners.remove(sensor, listener);
  }

  @Implementation
  protected void unregisterListener(SensorEventListener listener) {
    for (Sensor sensor : new ArrayList<>(listeners.keySet())) {
      listeners.remove(sensor, listener);
    }
  }

  public boolean hasListener(SensorEventListener listener) {
    return listeners.values().contains(listener);
  }

  /**
   * Returns the list of {@link SensorEventListener}s registered on this SensorManager. Note that
   * the list is unmodifiable, any attempt to modify it will throw an exception.
   */
  public List<SensorEventListener> getListeners() {
    return Collections.unmodifiableList(new ArrayList<>(listeners.values()));
  }

  /** Propagates the {@code event} to all registered listeners. */
  public void sendSensorEventToListeners(SensorEvent event) {
    for (SensorEventListener listener : new ArrayList<>(listeners.get(event.sensor))) {
      listener.onSensorChanged(event);
    }
  }

  public SensorEvent createSensorEvent() {
    return ReflectionHelpers.callConstructor(SensorEvent.class);
  }

  /**
   * Creates a {@link SensorEvent} with the given value array size, which the caller should set
   * based on the type of {@link Sensor} which is being emulated.
   *
   * <p>Callers can then specify individual values for the event. For example, for a proximity event
   * a caller may wish to specify the distance value:
   *
   * <pre>{@code
   * event.values[0] = distance;
   * }</pre>
   *
   * <p>See {@link SensorEvent#values} for more information about values.
   */
  public static SensorEvent createSensorEvent(int valueArraySize) {
    checkArgument(valueArraySize > 0);
    ClassParameter<Integer> valueArraySizeParam = new ClassParameter<>(int.class, valueArraySize);
    return ReflectionHelpers.callConstructor(SensorEvent.class, valueArraySizeParam);
  }

  @Implementation(minSdk = O)
  protected Object createDirectChannel(MemoryFile mem) {
    return ReflectionHelpers.callConstructor(SensorDirectChannel.class,
        ClassParameter.from(SensorManager.class, realObject),
        ClassParameter.from(int.class, 0),
        ClassParameter.from(int.class, SensorDirectChannel.TYPE_MEMORY_FILE),
        ClassParameter.from(long.class, mem.length()));
  }
}
