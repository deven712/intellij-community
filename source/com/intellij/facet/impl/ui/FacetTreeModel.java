/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetInfo;
import com.intellij.facet.FacetType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Collection;

/**
 * @author nik
 */
public class FacetTreeModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ui.FacetTreeModel");
  private static final FacetInfo ROOT = new FacetInfo(null, "", null, null);
  private final List<FacetInfo> myFacetInfos = new ArrayList<FacetInfo>();
  private final BidirectionalMap<FacetInfo, FacetInfo> myParents = new BidirectionalMap<FacetInfo, FacetInfo>();

  public void addFacetInfo(FacetInfo info) {
    myFacetInfos.add(info);
    myParents.put(info, null2Root(info.getUnderlyingFacet()));
  }

  private static @NotNull FacetInfo null2Root(@Nullable FacetInfo info) {
    return info == null ? ROOT : info;
  }

  private static @Nullable FacetInfo root2Null(@NotNull FacetInfo info) {
    return info == ROOT ? null : info;
  }

  public FacetInfo[] getFacetInfos() {
    return myFacetInfos.toArray(new FacetInfo[myFacetInfos.size()]);
  }

  public void removeFacetInfo(@NotNull FacetInfo info) {
    final boolean removed = myFacetInfos.remove(info);
    LOG.assertTrue(removed);
    myParents.remove(info);
  }

  @Nullable
  public FacetInfo getParent(@NotNull FacetInfo info) {
    return root2Null(myParents.get(info));
  }

  @NotNull
  public List<FacetInfo> getChildren(@Nullable FacetInfo info) {
    final List<FacetInfo> list = myParents.getKeysByValue(null2Root(info));
    if (list == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(list);
  }

  public List<FacetInfo> getTopLevelFacets() {
    return getChildren(null);
  }

  public void deleteFacetWithChildren(final FacetInfo info) {
    final List<FacetInfo> children = getChildren(info);
    for (FacetInfo child : children) {
      deleteFacetWithChildren(child);
    }
    removeFacetInfo(info);
  }

  @Nullable
  public FacetInfo findNearestFacet(@NotNull FacetInfo info) {
    final FacetInfo parent = getParent(info);
    final List<FacetInfo> children = getChildren(parent);
    int index = children.indexOf(info);
    if (index < children.size() - 1) {
      return children.get(index + 1);
    }
    if (index > 0) {
      return children.get(index - 1);
    }
    return parent;
  }

  public boolean hasFacetOfType(final @Nullable FacetInfo parent, final FacetTypeId typeId) {
    final List<FacetInfo> list = getChildren(parent);
    for (FacetInfo info : list) {
      if (info.getFacetType().getId() == typeId) {
        return true;
      }
    }
    return false;
  }

  public Collection<FacetInfo> getFacetInfos(final FacetType<?, ?> type) {
    final FacetInfo[] facetInfos = getFacetInfos();
    List<FacetInfo> infos = new ArrayList<FacetInfo>();
    for (FacetInfo facetInfo : facetInfos) {
      if (facetInfo.getFacetType().equals(type)) {
        infos.add(facetInfo);
      }
    }
    return infos;
  }
}
