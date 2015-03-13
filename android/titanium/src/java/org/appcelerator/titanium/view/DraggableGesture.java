/**
 * An enhanced fork of the original TiDraggable module by Pedro Enrique,
 * allows for simple creation of "draggable" views.
 *
 * Copyright (C) 2013 Seth Benjamin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * -- Original License --
 *
 * Copyright 2012 Pedro Enrique
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.appcelerator.titanium.view;

import java.util.HashMap;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.ReusableProxy;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiUIView;

import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;

public class DraggableGesture extends ReusableProxy implements OnTouchListener
{
	protected TiUIView draggableView;
	protected TiViewProxy draggableProxy;
	protected VelocityTracker velocityTracker;
	protected ViewConfiguration vc;
	protected int threshold;
	protected double lastX = 0;
	protected double lastY = 0;
	protected double distanceX = 0;
	protected double distanceY = 0;
    protected double startLeft = 0;
    protected double startTop = 0;
	protected double lastLeft;
	protected double lastTop;
	public boolean isBeingDragged = false;
	
	private TiDimension minLeft = null;
	private TiDimension maxLeft = null;
	private TiDimension minTop = null;
	private TiDimension maxTop = null;
	private TiDimension maxRight = null;
	private TiDimension maxBottom = null;
	private boolean enabled = true;
	private boolean xAxis = false;
	private boolean yAxis = false;
	private boolean ensureRight = false;
	private boolean ensureBottom = false;
	private Object[] maps = null;
	
	@Override
	public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
	    switch (key) {
	    case "enabled":
            enabled  = TiConvert.toBoolean(newValue, true);
            break;
	    case "axis":
	        String axis = TiConvert.toString(newValue, null);
	        xAxis = false;
	        yAxis = false;
	        if (axis != null) {
	            if (axis.equals("x")) {
	                xAxis = true;
	            } else if (axis.equals("y")) {
                    yAxis = true;
                }
	        }
            break;
        case "minLeft":
            minLeft  = TiConvert.toTiDimension(newValue, TiDimension.TYPE_LEFT);
            break;
        case "maxLeft":
            maxLeft  = TiConvert.toTiDimension(newValue, TiDimension.TYPE_LEFT);
            break;
	    case "minTop":
	        minTop  = TiConvert.toTiDimension(newValue, TiDimension.TYPE_TOP);
            break;
	    case "maxTop":
	        maxTop  = TiConvert.toTiDimension(newValue, TiDimension.TYPE_TOP);
            break;
	    case "maxRight":
            maxRight  = TiConvert.toTiDimension(newValue, TiDimension.TYPE_RIGHT);
            break;
	    case "maxBottom":
	        maxBottom  = TiConvert.toTiDimension(newValue, TiDimension.TYPE_TOP);
            break;
        default:
            break;
        }
    }

	public DraggableGesture(TiViewProxy proxy, TiUIView view)
	{
	    super();
		this.draggableProxy = proxy;
		this.draggableView = view;
		this.vc = ViewConfiguration.get(this.draggableView.getOuterView().getContext());
		this.threshold = vc.getScaledPagingTouchSlop();
		
		this.prepareMappedProxies();
	}

	public void determineDrag(MotionEvent event)
	{
		double xDelta = Math.abs(event.getRawX() - this.lastX);
		double yDelta = Math.abs(event.getRawY() - this.lastY);

		this.isBeingDragged = xDelta > this.threshold || yDelta > this.threshold;
	}

	@Override
	public boolean onTouch(View view, MotionEvent event) {
		
		if (!enabled)
		{
			return false;
		}

		if (! this.isBeingDragged && event.getAction() == MotionEvent.ACTION_MOVE)
		{
			this.determineDrag(event);
			if (this.isBeingDragged) {
	           this.startDrag(event);
			}
		}

		if (! this.isBeingDragged) {
			return false;
		}

		if (this.velocityTracker != null)
		{
			this.velocityTracker.addMovement(event);
		}

		switch(event.getAction())
		{
			case MotionEvent.ACTION_DOWN:
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				this.stopDrag(event);
				break;
			case MotionEvent.ACTION_MOVE:
				this.drag(event);
				break;
		}

		return true;
	}
	
	public Integer getDimensionAsPixels(String key, View parent)
    {
        KrollDict props = this.getProperties();
        
        if (props.containsKeyAndNotNull(key) && props.get(key) instanceof TiDimension)
        {
            TiDimension dimension = (TiDimension) props.get(key);

            return dimension.getAsPixels(parent);
        }

        return null;
    }
	
	public void drag(MotionEvent event)
	{
        View viewToDrag = this.draggableView.getOuterView();
        View parentView = (View) viewToDrag.getParent();
		
		
		float screenX = event.getRawX();
		float screenY = event.getRawY();
		
		if (this.isBeingDragged)
		{
			boolean noAxis = ! xAxis && ! yAxis;

            double leftEdge = screenX + startLeft;
			double topEdge = screenY + startTop;

			if (xAxis)
			{
				topEdge = viewToDrag.getTop();
			}
			if (yAxis)
			{
				leftEdge = viewToDrag.getLeft();
			}

			if (noAxis || xAxis)
			{
				if (minLeft != null)
				{
					double _minLeft = minLeft.getAsPixels(parentView);

					if (leftEdge <= _minLeft)
					{
						leftEdge = _minLeft;
					}
				}

				if (maxRight != null)
                {
                    double _maxRight = maxRight.getAsPixels(parentView);
                    double width = viewToDrag.getWidth();

                    if (leftEdge + width >= _maxRight)
                    {
                        leftEdge = _maxRight - width;
                    }
                } else if (maxLeft != null)
				{
					double _maxLeft = maxLeft.getAsPixels(parentView);

					if (leftEdge >= _maxLeft)
					{
						leftEdge = _maxLeft;
					}
				}
			}

			if (noAxis || yAxis)
			{
				if (minTop != null)
				{
					double _minTop = minTop.getAsPixels(parentView);

					if (topEdge <= _minTop)
					{
						topEdge = _minTop;
					}
				}

				if (maxBottom != null)
                {
                    double _maxBottom = maxBottom.getAsPixels(parentView);
                    double height = viewToDrag.getHeight();

                    if (leftEdge + height >= _maxBottom)
                    {
                        leftEdge = _maxBottom - height;
                    }
                } else if (maxTop != null) {
					double _maxTop = maxTop.getAsPixels(parentView);

					if (topEdge >= _maxTop)
					{
						topEdge = _maxTop;
					}
				}
			}
			
			double translationLeft = lastLeft - leftEdge;
			double translationTop = lastTop - topEdge;

			translateMappedProxies(translationLeft, translationTop);
			
			Float ensureRightValue = null;
			Float ensureBottomValue = null;
			
			if (ensureRight)
			{
				ensureRightValue = (float) -leftEdge;
			}
			
			if (ensureBottom)
			{
				ensureBottomValue = (float) -topEdge;
			}

			this.setViewPosition(draggableProxy, viewToDrag, (float) topEdge, (float) leftEdge, ensureBottomValue, ensureRightValue);

			distanceX += Math.abs(lastLeft - leftEdge);
			distanceY += Math.abs(lastTop - topEdge);
			lastLeft = leftEdge;
			lastTop = topEdge;

			if (draggableProxy.hasListeners("dragmove", false))
			{
				this.velocityTracker.computeCurrentVelocity(1000);

				KrollDict eventDict = new KrollDict();
				KrollDict velocityDict = new KrollDict();

				velocityDict.put("x", this.velocityTracker.getXVelocity());
				velocityDict.put("y", this.velocityTracker.getYVelocity());
				eventDict.put("left", viewToDrag.getLeft());
				eventDict.put("top", viewToDrag.getTop());
				eventDict.put("velocity", velocityDict);

				draggableProxy.fireEvent("dragmove", eventDict, false, false);
			}
		}
	}

	public void startDrag(MotionEvent event)
	{
		View viewToDrag = this.draggableView.getOuterView();
		
		this.lastX = event.getRawX();
		this.lastY = event.getRawY();

		float screenX = event.getRawX();
		float screenY = event.getRawY();

        this.startLeft = viewToDrag.getLeft() - screenX;
        this.startTop = viewToDrag.getTop() - screenY;
		this.lastLeft = viewToDrag.getLeft();
		this.lastTop = viewToDrag.getTop();
		this.distanceX = this.distanceY = 0;

		if (this.velocityTracker == null)
		{
			this.velocityTracker = VelocityTracker.obtain();
		}

		if (draggableProxy.hasListeners("dragstart", false))
		{
			this.velocityTracker.computeCurrentVelocity(1000);

			KrollDict velocityDict = new KrollDict();
			
			velocityDict.put("x", this.velocityTracker.getXVelocity());
			velocityDict.put("y", this.velocityTracker.getYVelocity());
			
			KrollDict eventDict = new KrollDict();
			
			eventDict.put("left", viewToDrag.getLeft());
			eventDict.put("top", viewToDrag.getTop());
			eventDict.put("velocity", velocityDict);

			draggableProxy.fireEvent("dragstart", eventDict, false, false);
		}
	}

	public void stopDrag(MotionEvent event)
	{
		if (this.isBeingDragged)
		{
			this.isBeingDragged = false;
			
			this.finalizeMappedTranslations();

			if (draggableProxy.hasListeners("dragend", false))
			{
				View viewToDrag = this.draggableView.getOuterView();

				this.velocityTracker.computeCurrentVelocity(1000);
				
				KrollDict distanceDict = new KrollDict();
				
				distanceDict.put("x", distanceX);
				distanceDict.put("y", distanceY);
				
				KrollDict velocityDict = new KrollDict();
				
				velocityDict.put("x", this.velocityTracker.getXVelocity());
				velocityDict.put("y", this.velocityTracker.getYVelocity());
				
				KrollDict eventDict = new KrollDict();
				
				eventDict.put("left", viewToDrag.getLeft());
				eventDict.put("top", viewToDrag.getTop());
				eventDict.put("velocity", velocityDict);
                eventDict.put("distance", distanceDict);
                eventDict.put("cancel", event.getAction() != MotionEvent.ACTION_UP);

				draggableProxy.fireEvent("dragend", eventDict, false, false);
			}

			this.velocityTracker.clear();
		}
	}

	protected void prepareMappedProxies()
	{
		if (maps != null)
		{
			for (Object mapObject : maps)
			{
				@SuppressWarnings({ "rawtypes", "unchecked" })
				KrollDict map = new KrollDict((HashMap) mapObject);
				TiViewProxy mappedProxy = (TiViewProxy) map.get("view");
				View mappedView = mappedProxy.peekView().getOuterView();
		        View parentView = (View) mappedView.getParent();

				if (map.containsKeyAndNotNull("constrain"))
				{
					KrollDict constraints = map.getKrollDict("constrain");
					KrollDict constraintX = constraints.getKrollDict("x");
					KrollDict constraintY = constraints.getKrollDict("y");
					
					boolean didModifyPosition = false;

					double parallaxAmount = map.containsKeyAndNotNull("parallaxAmount") ? TiConvert.toDouble(map, "parallaxAmount") : 1;
					double newLeft = 0;
					double newTop = 0;

					if (constraintX != null && constraintX.containsKeyAndNotNull("start"))
					{
						double xStart = TiConvert.toTiDimension(constraintX, "start", TiDimension.TYPE_LEFT).getAsPixels(parentView);
						
						if (constraintX.containsKeyAndNotNull("end"))
						{
							double xEnd = TiConvert.toTiDimension(constraintX, "end", TiDimension.TYPE_LEFT).getAsPixels(parentView);
							
							newLeft = xStart / parallaxAmount - xEnd;
						}
						else
						{
							newLeft = mappedView.getLeft() / parallaxAmount;
						}
						
						didModifyPosition = true;
					}

					if (constraintY != null && constraintY.containsKeyAndNotNull("start"))
					{
						double yStart = TiConvert.toTiDimension(constraintX, "start", TiDimension.TYPE_TOP).getAsPixels(parentView);
						
						if (constraintY.containsKeyAndNotNull("end"))
						{
							double yEnd = TiConvert.toTiDimension(constraintX, "end", TiDimension.TYPE_TOP).getAsPixels(parentView);
							
							newTop = yStart / parallaxAmount - yEnd;
						}
						else
						{
							newTop = mappedView.getTop() / parallaxAmount;
						}
						
						didModifyPosition = true;
					}

					if (didModifyPosition)
					{
						this.setViewPosition(mappedProxy, mappedView, (float) newTop, (float) newLeft, null, null);
					}
				}
			}
		}
	}
	
	protected void translateMappedProxies(double translationX, double translationY)
	{
		if (maps != null)
		{
			for (Object mapObject : maps)
			{
				@SuppressWarnings({ "rawtypes", "unchecked" })
				KrollDict map = new KrollDict((HashMap) mapObject);
				TiViewProxy mappedProxy = (TiViewProxy) map.get("view");
				View mappedView = mappedProxy.peekView().getOuterView();
				double parallaxAmount = map.containsKeyAndNotNull("parallaxAmount") ? TiConvert.toDouble(map, "parallaxAmount") : 1;
				
				translationX = mappedView.getTranslationX() - translationX / parallaxAmount;
				translationY = mappedView.getTranslationY() - translationY / parallaxAmount;

//				mappedView.setTranslationX((float) translationX);
//				mappedView.setTranslationY((float) translationY);
			}
		}
	}
	
	protected void finalizeMappedTranslations()
	{
        if (maps != null)
		{
            for (Object mapObject : maps)
			{
				@SuppressWarnings({ "rawtypes", "unchecked" })
				KrollDict map = new KrollDict((HashMap) mapObject);
				TiViewProxy mappedProxy = (TiViewProxy) map.get("view");
				View mappedView = mappedProxy.peekView().getOuterView();
				
				this.setViewPosition(mappedProxy, mappedView, mappedView.getY(), mappedView.getX(), null, null);
			}
		}
	}
	
	protected void setViewPosition(KrollProxy proxy, View view, Float top, Float left, Float bottom, Float right)
	{
		TiCompositeLayout.LayoutParams layout = (TiCompositeLayout.LayoutParams) view.getLayoutParams();

		layout.optionLeft = new TiDimension(left, TiDimension.TYPE_LEFT, TypedValue.COMPLEX_UNIT_PX);
		layout.optionTop = new TiDimension(top, TiDimension.TYPE_TOP, TypedValue.COMPLEX_UNIT_PX);
		
		if (right != null)
		{
			layout.optionRight = new TiDimension(right, TiDimension.TYPE_RIGHT, TypedValue.COMPLEX_UNIT_PX);
		} else {
		    layout.optionRight = null;
		}
		
		if (bottom != null)
		{
			layout.optionBottom = new TiDimension(bottom, TiDimension.TYPE_BOTTOM, TypedValue.COMPLEX_UNIT_PX);
		} else {
            layout.optionBottom = null;
        }

		view.setLayoutParams(layout);
//		view.setTranslationX(0);
//		view.setTranslationY(0);
		
		proxy.setProperty("left", left);
		proxy.setProperty("top", top);
		
		if (right != null)
		{
			proxy.setProperty("right", right);
		}
		
		if (bottom != null)
		{
			proxy.setProperty("bottom", bottom);
		}
	}
	
	@Override
	protected void doSetProperty(String name, Object value) {
    }

    @Override
    protected void doUpdateKrollObjectProperties() {
    }

    @Override
    protected void doUpdateKrollObjectProperties(HashMap<String, Object> props) {
    }
}