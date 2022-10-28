package org.eclipse.eripgrep.ui.model;

import static org.eclipse.eripgrep.ui.ERipGrepViewPart.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.eripgrep.model.SearchedProject;

public class SeeAll {

  public static final int MAX_NUMBER = 50;

  private static final ConcurrentHashMap<Object, SeeAll> cache = new ConcurrentHashMap<>();

  private final Object element;
  private final List<?> objects;

  private SeeAll(Object element, List<?> objects) {
    this.element = element;
    this.objects = objects;
  }

  public Object[] toArray() {
    List<Object> otherObjects = new ArrayList<>();
    for (int i = MAX_NUMBER; i < objects.size(); i++) {
      otherObjects.add(objects.get(i));
    }
    if (ALPHABETICAL_SORT && element instanceof SearchedProject) {
      otherObjects.sort(MATCHINGFILE_COMPARATOR);
    }
    return otherObjects.toArray();
  }

  public List<?> getUnderlyingObjects() {
    return objects;
  }

  public static SeeAll getOrCreate(Object object, List<?> list) {
    return cache.computeIfAbsent(object, o -> new SeeAll(object, list));
  }

  public static void clear() {
    cache.clear();
  }
}