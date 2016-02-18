package org.appcelerator.titanium.transition;

import org.appcelerator.titanium.animation.AlphaProperty;

import android.view.View;

import android.animation.ObjectAnimator;

public class TransitionFade extends Transition {
	public TransitionFade(int subtype, boolean isOut, int duration) {
		super(subtype, isOut, duration, 300);
	}
	public int getType(){
		return TransitionHelper.Types.kTransitionFade.ordinal();
	}
	protected void prepareAnimators(View inTarget, View outTarget) {
		inAnimator = ObjectAnimator
				.ofFloat(null, new AlphaProperty(), 0.0f, 1.0f);
		inAnimator.setDuration(duration);
		
		outAnimator = ObjectAnimator
				.ofFloat(null, new AlphaProperty(), 1.0f, 0.0f);
		outAnimator.setDuration(duration);
	}
	public void setTargets(boolean reversed, View holder, View inTarget, View outTarget) {
		super.setTargets(reversed, holder, inTarget, outTarget);
		if (inTarget != null) inTarget.setAlpha(0.0f);
	}
	
    @Override
    public void transformView(View view, float position) {
        boolean out = (position < 0);
        if (!TransitionHelper.isPushSubType(subType)) {
            out = !out;
        }
        float alpha = Math.max(0, 1 - Math.abs(position));

        view.setAlpha(alpha);
        view.setVisibility((alpha <= 0)?View.INVISIBLE:View.VISIBLE);
    }
}