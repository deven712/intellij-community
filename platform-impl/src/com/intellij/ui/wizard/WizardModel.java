/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.wizard;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class WizardModel {

  private final List<WizardStep> mySteps = new ArrayList<WizardStep>();

  private WizardStep myCurrentStep;
  private WizardNavigationState myCurrentState;
  private JComponent myCurrentComponent;

  private WizardCallback myCallback;

  private boolean myDropped = false;
  private boolean myAchieved = false;
  private final String myTitle;

  public WizardModel(String title) {
    myTitle = title;
  }

  public final WizardStep add(WizardStep step) {
    mySteps.add(step);
    return step;
  }

  public final WizardStep addBefore(WizardStep beforeWhat, WizardStep toAdd) {
    int i = mySteps.indexOf(beforeWhat);
    mySteps.add(i, toAdd);
    return toAdd;
  }

  public final WizardStep addAfter(WizardStep afterWhat, WizardStep toAdd) {
    int i = mySteps.indexOf(afterWhat);
    mySteps.add(i + 1, toAdd);
    return toAdd;
  }

  public final void next() {
    checkModel();

    WizardStep nextStep = getCurrentStep().onNext(this);

    if (nextStep == WizardStep.FORCED_GOAL_DROPPED) {
      cancel();
      return;
    }

    if (nextStep == WizardStep.FORCED_GOAL_ACHIEVED) {
      finish();
      return;
    }

    if (nextStep == null) {
      nextStep = getNextFor(getCurrentStep());
    }
    prepareCurrentStep(nextStep);

    getCallback().onStepChanged();
  }

  public final void previous() {
    checkModel();

    WizardStep previousStep = getCurrentStep().onPrevious(this);
    if (previousStep == null) {
      previousStep = getPreviousFor(getCurrentStep());
    }

    prepareCurrentStep(previousStep);

    getCallback().onStepChanged();
  }

  public final void finish() {
    checkModel();
    setFinishedAs(getCurrentStep().onFinish());

    if (isWizardGoalAchieved()) {
      getCallback().onWizardGoalAchieved();
    } else {
      getCallback().onWizardGoalDropped();
    }
  }

  public final void cancel() {
    checkModel();
    boolean canCancel = getCurrentStep().onCancel();

    if (!canCancel) return;

    getCallback().onWizardGoalDropped();

    setFinishedAs(false);
  }

  public final WizardStep getCurrentStep() {
    checkModel();

    if (myCurrentStep == null) {
      prepareCurrentStep(mySteps.get(0));
    }

    return myCurrentStep;
  }

  public final JComponent getCurrentComponent() {
    checkModel();
    getCurrentStep();
    return myCurrentComponent;
  }

  public final WizardNavigationState getCurrentNavigationState() {
    checkModel();
    getCurrentStep();
    return myCurrentState;
  }

  public boolean isFirst(WizardStep step) {
    return mySteps.indexOf(step) == 0;
  }

  public boolean isLast(WizardStep step) {
    return mySteps.indexOf(step) == mySteps.size() - 1;
  }

  public final WizardStep getNextFor(WizardStep wizardStep) {
    return mySteps.get(mySteps.indexOf(wizardStep) + 1);
  }

  public final WizardStep getPreviousFor(WizardStep wizardStep) {
    return mySteps.get(mySteps.indexOf(wizardStep) - 1);
  }

  private void checkModel() {
    if (mySteps.size() == 0) {
      throw new IllegalStateException("Not steps were added");
    }
  }

  private void prepareCurrentStep(WizardStep currentStep) {
    myCurrentStep = currentStep;

    myCurrentState = new WizardNavigationState(this);
    myCurrentState.NEXT.setEnabled(!isLast(myCurrentStep));
    myCurrentState.PREVIOUS.setEnabled(!isFirst(myCurrentStep));
    myCurrentState.FINISH.setEnabled(isLast(myCurrentStep));
    myCurrentState.CANCEL.setEnabled(true);

    myCurrentComponent = myCurrentStep.prepare(myCurrentState);
  }

  void setCallback(WizardCallback callback) {
    myCallback = callback;
  }

  private WizardCallback getCallback() {
    return myCallback != null ? myCallback : WizardCallback.EMPTY;
  }

  public final boolean isWizardGoalDropped() {
    return myDropped;
  }

  public final boolean isWizardGoalAchieved() {
    return myAchieved;
  }

  private void setFinishedAs(boolean goalAchieved) {
    myAchieved = goalAchieved;
    myDropped = !goalAchieved;
  }

  public boolean isDone() {
    return myAchieved || myDropped;
  }

  public String getTitle() {
    return myTitle;
  }
}
