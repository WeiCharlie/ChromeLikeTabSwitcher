/*
 * Copyright 2016 - 2017 Michael Rapp
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package de.mrapp.android.tabswitcher.layout.tablet;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.FrameLayout;

import java.util.Collections;

import de.mrapp.android.tabswitcher.Animation;
import de.mrapp.android.tabswitcher.R;
import de.mrapp.android.tabswitcher.Tab;
import de.mrapp.android.tabswitcher.TabSwitcher;
import de.mrapp.android.tabswitcher.iterator.AbstractItemIterator;
import de.mrapp.android.tabswitcher.iterator.ItemIterator;
import de.mrapp.android.tabswitcher.layout.AbstractDragEventHandler;
import de.mrapp.android.tabswitcher.layout.AbstractTabSwitcherLayout;
import de.mrapp.android.tabswitcher.layout.Arithmetics;
import de.mrapp.android.tabswitcher.layout.Arithmetics.Axis;
import de.mrapp.android.tabswitcher.model.AbstractItem;
import de.mrapp.android.tabswitcher.model.AddTabItem;
import de.mrapp.android.tabswitcher.model.ItemComparator;
import de.mrapp.android.tabswitcher.model.State;
import de.mrapp.android.tabswitcher.model.TabItem;
import de.mrapp.android.tabswitcher.model.TabSwitcherModel;
import de.mrapp.android.tabswitcher.util.ThemeHelper;
import de.mrapp.android.util.view.AttachedViewRecycler;
import de.mrapp.android.util.view.ViewRecycler;

import static de.mrapp.android.util.Condition.ensureNotEqual;

/**
 * A layout, which implements the functionality of a {@link TabSwitcher} on tablets.
 *
 * @author Michael Rapp
 * @since 1.0.0
 */
public class TabletTabSwitcherLayout extends AbstractTabSwitcherLayout<Void>
        implements Tab.Callback {

    /**
     * A comparator, which allows to compare two instances of the class {@link AbstractItem}. The
     * item, which corresponds to the currently selected tab, is always sorted before all other
     * items.
     */
    private static class TabletItemComparator extends ItemComparator {

        /**
         * Creates a new comparator, which allows to compare two instances of the class {@link
         * AbstractItem}. The item, which corresponds to the currently selected tab, is always
         * sorted before all other items.
         *
         * @param tabSwitcher
         *         The tab switcher, the items, which should be compared by the comparator, belong
         *         to, as a instance of the class {@link TabSwitcher}. The tab switcher may not be
         *         null
         */
        TabletItemComparator(@NonNull final TabSwitcher tabSwitcher) {
            super(tabSwitcher);
        }

        @Override
        public int compare(final AbstractItem o1, final AbstractItem o2) {
            if (o1 instanceof AddTabItem && o2 instanceof AddTabItem) {
                return 0;
            } else if (o1 instanceof AddTabItem) {
                return -1;
            } else if (o2 instanceof AddTabItem) {
                return 1;
            } else {
                TabItem tabItem1 = (TabItem) o1;
                TabItem tabItem2 = (TabItem) o2;
                Tab tab1 = tabItem1.getTab();
                Tab tab2 = tabItem2.getTab();
                int index1 = getTabSwitcher().indexOf(tab1);
                index1 = index1 == -1 ? o1.getIndex() : index1;
                int index2 = getTabSwitcher().indexOf(tab2);
                index2 = index2 == -1 ? o2.getIndex() : index2;
                ensureNotEqual(index1, -1, "Tab " + tab1 + " not contained by tab switcher",
                        RuntimeException.class);
                ensureNotEqual(index2, -1, "Tab " + tab2 + " not contained by tab switcher",
                        RuntimeException.class);
                int selectedTabIndex = getTabSwitcher().getSelectedTabIndex();

                if (index1 < selectedTabIndex) {
                    if (index2 == selectedTabIndex) {
                        return 1;
                    } else if (index2 > selectedTabIndex) {
                        return -1;
                    } else {
                        return index1 >= index2 ? -1 : 1;
                    }
                } else if (index1 > selectedTabIndex) {
                    if (index2 == selectedTabIndex) {
                        return 1;
                    } else if (index2 > selectedTabIndex) {
                        return index1 < index2 ? -1 : 1;
                    } else {
                        return 1;
                    }
                } else {
                    return -1;
                }
            }
        }
    }

    /**
     * The number of tabs, which are contained by a stack.
     */
    private final int stackedTabCount;

    /**
     * The maximum width of a tab in pixels.
     */
    private final int maxTabWidth;

    /**
     * The minimum width of a tab in pixels.
     */
    private final int minTabWidth;

    /**
     * The offset between two neighboring tabs in pixels.
     */
    private final int tabOffset;

    /**
     * The width of a button, which allows to add a new tab.
     */
    private final int addTabButtonWidth;

    /**
     * The offset between a button, which allows to add a new tab, and a neighboring tab.
     */
    private final int addTabButtonOffset;

    /**
     * The default background color of tabs.
     */
    private final ColorStateList tabBackgroundColor;

    /**
     * The default background color of a tab's content.
     */
    private final int tabContentBackgroundColor;

    /**
     * The drag handler, which is used by the layout.
     */
    private TabletDragEventHandler dragHandler;

    /**
     * The view recycler, which allows to recycle the views, which are associated with of tabs.
     */
    private ViewRecycler<Tab, Void> contentViewRecycler;

    /**
     * The adapter, which allows to inflate the views, which are used to visualize tabs.
     */
    private TabletRecyclerAdapter recyclerAdapter;

    /**
     * The view recycler, which allows to recycle the views, which are used to visualize tabs.
     */
    private AttachedViewRecycler<AbstractItem, Void> tabViewRecycler;

    /**
     * The layout's primary toolbar.
     */
    private Toolbar primaryToolbar;

    /**
     * The layout's secondary toolbar.
     */
    private Toolbar secondaryToolbar;

    /**
     * The view group, which contains the tab switcher's tabs.
     */
    private ViewGroup tabContainer;

    /**
     * The view, which is used to display a border below the tab switcher's tabs.
     */
    private View borderView;

    /**
     * The view group, which contains the children of the tab switcher's tabs.
     */
    private ViewGroup contentContainer;

    /**
     * Adapts the margins of the tab container and the toolbars.
     */
    private void adaptTabContainerAndToolbarMargins() {
        FrameLayout.LayoutParams tabContainerLayoutParams =
                (FrameLayout.LayoutParams) tabContainer.getLayoutParams();
        tabContainerLayoutParams.setMargins(getModel().getPaddingLeft(), getModel().getPaddingTop(),
                getModel().getPaddingRight(), 0);
        FrameLayout.LayoutParams primaryToolbarLayoutParams =
                (FrameLayout.LayoutParams) primaryToolbar.getLayoutParams();
        primaryToolbarLayoutParams
                .setMargins(getModel().getPaddingLeft(), 0, getModel().getPaddingRight(), 0);
        FrameLayout.LayoutParams secondaryToolbarLayoutParams =
                (FrameLayout.LayoutParams) secondaryToolbar.getLayoutParams();
        secondaryToolbarLayoutParams
                .setMargins(getModel().getPaddingLeft(), 0, getModel().getPaddingRight(), 0);
    }

    /**
     * Adapts the color of the border, which is shown below the tab switcher's tabs.
     */
    private void adaptBorderColor() {
        Tab selectedTab = getModel().getSelectedTab();
        ColorStateList colorStateList =
                selectedTab != null ? selectedTab.getBackgroundColor() : null;

        if (colorStateList == null) {
            colorStateList = getModel().getTabBackgroundColor();

            if (colorStateList == null) {
                colorStateList = tabBackgroundColor;
            }
        }

        int[] stateSet = new int[]{android.R.attr.state_selected};
        int color = colorStateList.getColorForState(stateSet, colorStateList.getDefaultColor());
        Drawable background = borderView.getBackground();
        background.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
    }

    /**
     * Adapts the background color of the currently selected tab's content.
     */
    private void adaptTabContentBackgroundColor() {
        Tab selectedTab = getModel().getSelectedTab();
        int color = selectedTab != null ? selectedTab.getContentBackgroundColor() : -1;

        if (color == -1) {
            color = getModel().getTabContentBackgroundColor();
        }

        contentContainer.setBackgroundColor(color != -1 ? color : tabContentBackgroundColor);
    }

    /**
     * Inflates the content, which is associated with a specific tab.
     *
     * @param tab
     *         The tab, whose content should be inflated, as an instance of the class {@link Tab}.
     *         The tab may not be null
     */
    private void inflateContent(@NonNull final Tab tab) {
        Pair<View, ?> pair = contentViewRecycler.inflate(tab, contentContainer);
        View view = pair.first;
        FrameLayout.LayoutParams layoutParams =
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT);
        layoutParams.setMargins(getModel().getPaddingLeft(), 0, getModel().getPaddingRight(),
                getModel().getPaddingBottom());
        contentContainer.addView(view, layoutParams);
    }

    /**
     * Calculates and returns the width of the tabs, depending on the total number of tabs, which
     * are currently contained by the tab switcher.
     *
     * @return The width, which has been calculated, in pixels as an {@link Integer} value
     */
    private int calculateTabWidth() {
        return maxTabWidth;
    }

    /**
     * Calculates and returns the space between two neighboring tabs.
     *
     * @return The space, which has been calculated, in pixels as an {@link Integer} value
     */
    private int calculateTabSpacing() {
        return calculateTabWidth() - tabOffset;
    }

    /**
     * Calculates and returns the space between a button, which allows to add a new tab, and a
     * neighboring tab.
     *
     * @return The space, which has been calculated, in pixels as an {@link Integer} value
     */
    private int calculateAddTabButtonSpacing() {
        return getModel().isAddTabButtonShown() ? addTabButtonWidth + addTabButtonOffset : 0;
    }

    /**
     * Calculates and returns the width of the tab container.
     *
     * @return The width of the tab container in pixels as a {@link Float} value
     */
    private float calculateTabContainerWidth() {
        float size = getArithmetics().getSize(Axis.DRAGGING_AXIS, getTabSwitcher());
        int padding = getModel().getPaddingRight() + getModel().getPaddingLeft();
        Toolbar[] toolbars = getToolbars();
        float primaryToolbarSize = getModel().areToolbarsShown() && toolbars != null ?
                Math.max(0, toolbars[TabSwitcher.PRIMARY_TOOLBAR_INDEX].getWidth() - tabOffset) : 0;
        float secondaryToolbarSize = getModel().areToolbarsShown() && toolbars != null ? Math.max(0,
                toolbars[TabSwitcher.SECONDARY_TOOLBAR_INDEX].getWidth() -
                        (getModel().isAddTabButtonShown() ? addTabButtonOffset : 0)) : 0;
        return size - padding - primaryToolbarSize - secondaryToolbarSize;
    }

    /**
     * Calculates and returns the items when the tab switcher is shown initially.
     *
     * @param firstVisibleTabIndex
     *         The index of the first visible tab as an {@link Integer} value or -1, if the index is
     *         unknown
     * @param firstVisibleTabPosition
     *         The position of the first visible tab in pixels as a {@link Float} value or -1, if
     *         the position is unknown
     * @return An array, which contains the tab items, as an array of the type {@link AbstractItem}.
     * The array may not be null
     */
    @NonNull
    private AbstractItem[] calculateInitialItems(final int firstVisibleTabIndex,
                                                 final float firstVisibleTabPosition) {
        dragHandler.reset();
        setFirstVisibleIndex(-1);
        AbstractItem[] items = new AbstractItem[getItemCount()];

        if (items.length > 0) {
            int tabSpacing = calculateTabSpacing();
            int referenceIndex = firstVisibleTabIndex != -1 && firstVisibleTabPosition != -1 ?
                    firstVisibleTabIndex : 0;
            float referencePosition = firstVisibleTabIndex != -1 && firstVisibleTabPosition != -1 ?
                    firstVisibleTabPosition : -1;
            AbstractItem referenceItem = null;
            AbstractItemIterator iterator = new InitialItemIterator(items, false, referenceIndex);
            AbstractItem item;

            while ((item = iterator.next()) != null) {
                AbstractItem predecessor = iterator.previous();
                float position;

                if (item.getIndex() == referenceIndex && referencePosition != -1) {
                    referenceItem = item;
                    position = referencePosition;
                } else {
                    position = calculateMaxEndPosition(item.getIndex());
                }

                Pair<Float, State> pair = clipPosition(item.getIndex(), position, predecessor);
                item.getTag().setPosition(pair.first);
                item.getTag().setState(pair.second);

                if (getFirstVisibleIndex() == -1 && pair.second == State.FLOATING) {
                    setFirstVisibleIndex(item.getIndex());
                }
            }

            if (referenceIndex > 0 && referenceItem != null) {
                iterator = new InitialItemIterator(items, true, referenceIndex - 1);

                while ((item = iterator.next()) != null && item.getIndex() < referenceIndex) {
                    AbstractItem predecessor = iterator.peek();
                    float position = referenceItem.getTag().getPosition() +
                            ((item.getIndex() - referenceIndex) * tabSpacing);
                    Pair<Float, State> pair = clipPosition(item.getIndex(), position, predecessor);
                    item.getTag().setPosition(pair.first);
                    item.getTag().setState(pair.second);

                    if (pair.second == State.FLOATING) {
                        setFirstVisibleIndex(item.getIndex());
                    }
                }
            }
        }

        dragHandler.setCallback(this);
        return items;
    }

    /**
     * Creates and returns a layout listener, which allows to adapt the size and position of an
     * item, once its view has been inflated.
     *
     * @param item
     *         The item, whose view should be adapted, as an instance of the class {@link
     *         AbstractItem}. The item may not be null
     * @param layoutListener
     *         The layout lister, which should be notified, when the created listener is invoked, as
     *         an instance of the type {@link OnGlobalLayoutListener} or null, if no listener should
     *         be notified
     * @return The layout listener, which has been created, as an instance of the type {@link
     * OnGlobalLayoutListener}. The layout listener may not be null
     */
    @NonNull
    private OnGlobalLayoutListener createInflateViewLayoutListener(@NonNull final AbstractItem item,
                                                                   @Nullable final OnGlobalLayoutListener layoutListener) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                updateView(item);

                if (layoutListener != null) {
                    layoutListener.onGlobalLayout();
                }
            }

        };
    }

    /**
     * Creates a new layout, which implements the functionality of a {@link TabSwitcher} on tablets.
     *
     * @param tabSwitcher
     *         The tab switcher, the layout belongs to, as an instance of the class {@link
     *         TabSwitcher}. The tab switcher may not be null
     * @param model
     *         The model of the tab switcher, the layout belongs to, as an instance of the class
     *         {@link TabSwitcherModel}. The model may not be null
     * @param arithmetics
     *         The arithmetics, which should be used by the layout, as an instance of the type
     *         {@link Arithmetics}. The arithmetics may not be null
     * @param themeHelper
     *         The theme helper, which allows to retrieve resources, depending on the tab switcher's
     *         theme, as an instance of the class {@link ThemeHelper}. The theme helper may not be
     *         null
     */
    public TabletTabSwitcherLayout(@NonNull final TabSwitcher tabSwitcher,
                                   @NonNull final TabSwitcherModel model,
                                   @NonNull final Arithmetics arithmetics,
                                   @NonNull final ThemeHelper themeHelper) {
        super(tabSwitcher, model, arithmetics, themeHelper);
        Resources resources = tabSwitcher.getResources();
        stackedTabCount = resources.getInteger(R.integer.tablet_stacked_tab_count);
        maxTabWidth = resources.getDimensionPixelSize(R.dimen.tablet_tab_max_width);
        minTabWidth = resources.getDimensionPixelSize(R.dimen.tablet_tab_min_width);
        tabOffset = resources.getDimensionPixelSize(R.dimen.tablet_tab_offset);
        addTabButtonWidth = resources.getDimensionPixelSize(R.dimen.tablet_add_tab_button_width);
        addTabButtonOffset = resources.getDimensionPixelSize(R.dimen.tablet_add_tab_button_offset);
        tabBackgroundColor = themeHelper.getColorStateList(getTabSwitcher().getLayout(),
                R.attr.tabSwitcherTabBackgroundColor);
        tabContentBackgroundColor = getThemeHelper().getColor(getTabSwitcher().getLayout(),
                R.attr.tabSwitcherTabContentBackgroundColor);
    }

    @Override
    public final AbstractDragEventHandler<?> getDragHandler() {
        return dragHandler;
    }

    @Override
    protected final void onInflateLayout(@NonNull final LayoutInflater inflater,
                                         final boolean tabsOnly) {
        if (!tabsOnly) {
            inflater.inflate(R.layout.tablet_layout, getTabSwitcher(), true);
        }

        primaryToolbar = (Toolbar) getTabSwitcher().findViewById(R.id.primary_toolbar);
        secondaryToolbar = (Toolbar) getTabSwitcher().findViewById(R.id.secondary_toolbar);
        tabContainer = (ViewGroup) getTabSwitcher().findViewById(R.id.tab_container);
        borderView = getTabSwitcher().findViewById(R.id.border_view);
        contentContainer = (ViewGroup) getTabSwitcher().findViewById(R.id.content_container);
        contentViewRecycler = new ViewRecycler<>(inflater);
        recyclerAdapter = new TabletRecyclerAdapter(getTabSwitcher(), getModel(), getThemeHelper());
        getModel().addListener(recyclerAdapter);
        tabViewRecycler = new AttachedViewRecycler<>(tabContainer, inflater,
                Collections.reverseOrder(new TabletItemComparator(getTabSwitcher())));
        tabViewRecycler.setAdapter(recyclerAdapter);
        recyclerAdapter.setViewRecycler(tabViewRecycler);
        dragHandler = new TabletDragEventHandler(getTabSwitcher(), getArithmetics(), tabViewRecycler);
        adaptTabContainerAndToolbarMargins();
        adaptBorderColor();
        adaptTabContentBackgroundColor();
    }

    @Override
    protected final void onDetachLayout(final boolean tabsOnly) {
        // TODO: childViewRecycler.removeAll();
        // TODO: childViewRecycler.clearCache();
        if (!tabsOnly) {
            getModel().removeListener(recyclerAdapter);
        }
    }

    @Override
    protected final ViewRecycler<Tab, Void> getContentViewRecycler() {
        return contentViewRecycler;
    }

    @Override
    protected final AttachedViewRecycler<AbstractItem, Void> getTabViewRecycler() {
        return tabViewRecycler;
    }

    @Override
    protected final void inflateAndUpdateView(@NonNull final AbstractItem item,
                                              @Nullable final OnGlobalLayoutListener listener) {
        inflateView(item, createInflateViewLayoutListener(item, listener));
    }

    @Override
    protected final int getStackedTabCount() {
        return stackedTabCount;
    }

    @NonNull
    @Override
    protected final Pair<Float, State> calculatePositionAndStateWhenStackedAtStart(final int count,
                                                                                   final int index,
                                                                                   @Nullable final State predecessorState) {
        int selectedItemIndex =
                getModel().getSelectedTabIndex() + (getModel().isAddTabButtonShown() ? 1 : 0);
        float position;
        State state;

        if (index == 0 && getModel().isAddTabButtonShown()) {
            position = getStackedTabSpacing() * Math.min(count - 2, getStackedTabCount()) +
                    calculateTabSpacing() + addTabButtonOffset;
            state = State.FLOATING;
        } else if (index == selectedItemIndex) {
            position = getStackedTabSpacing() * Math.min(count - (index + 1), getStackedTabCount());
            state = State.STACKED_START_ATOP;
        } else if (index < selectedItemIndex) {
            if ((selectedItemIndex - index) < getStackedTabCount()) {
                position = (getStackedTabSpacing() *
                        Math.min(count - (selectedItemIndex + 1), getStackedTabCount())) +
                        (getStackedTabSpacing() * (selectedItemIndex - index));
                state = State.STACKED_END;
            } else {
                position = (getStackedTabSpacing() *
                        Math.min(count - (selectedItemIndex + 1), getStackedTabCount())) +
                        (getStackedTabSpacing() * getStackedTabCount());
                state = State.HIDDEN;
            }
        } else {
            if ((count - index) <= getStackedTabCount()) {
                position = getStackedTabSpacing() * (count - (index + 1));
                state = predecessorState == null || predecessorState == State.FLOATING ?
                        State.STACKED_START_ATOP : State.STACKED_START;
            } else {
                position = getStackedTabSpacing() * getStackedTabCount();
                state = predecessorState == null || predecessorState == State.FLOATING ?
                        State.STACKED_START_ATOP : State.HIDDEN;
            }
        }

        return Pair.create(position, state);
    }

    @NonNull
    @Override
    protected final Pair<Float, State> calculatePositionAndStateWhenStackedAtEnd(final int index) {
        float tabContainerWidth = calculateTabContainerWidth();
        int selectedTabIndex = getModel().getSelectedTabIndex();
        int selectedItemIndex = selectedTabIndex + (getModel().isAddTabButtonShown() ? 1 : 0);
        int i = getModel().isAddTabButtonShown() ? index - 1 : index;
        float position;
        State state;

        if (index == 0 && getModel().isAddTabButtonShown()) {
            position = tabContainerWidth - addTabButtonWidth;
            state = State.STACKED_END;
        } else if (index == selectedItemIndex) {
            position = tabContainerWidth - calculateAddTabButtonSpacing() - calculateTabSpacing() -
                    (getStackedTabSpacing() * Math.min(getStackedTabCount(), i));
            state = State.STACKED_END;
        } else if (index < selectedItemIndex) {
            if (i < getStackedTabCount()) {
                position =
                        tabContainerWidth - calculateAddTabButtonSpacing() - calculateTabSpacing() -
                                (getStackedTabSpacing() * i);
                state = State.STACKED_END;
            } else {
                position =
                        tabContainerWidth - calculateAddTabButtonSpacing() - calculateTabSpacing() -
                                (getStackedTabSpacing() * getStackedTabCount());
                state = State.STACKED_END;
            }
        } else {
            float selectedItemPosition =
                    tabContainerWidth - calculateAddTabButtonSpacing() - calculateTabSpacing() -
                            (getStackedTabSpacing() *
                                    Math.min(getStackedTabCount(), selectedTabIndex));

            if (index <= selectedItemIndex + getStackedTabCount()) {
                position = selectedItemPosition -
                        (getStackedTabSpacing() * (index - selectedItemIndex));
                state = State.STACKED_END;
            } else {
                position = selectedItemPosition - (getStackedTabSpacing() * getStackedTabCount());
                state = State.HIDDEN;
            }
        }

        return Pair.create(position, state);
    }

    @Override
    protected final float calculateMinStartPosition(final int index) {
        int thresholdPosition = (getItemCount() - 1) * calculateTabSpacing();
        float tabContainerWidth = calculateTabContainerWidth();

        if (thresholdPosition < tabContainerWidth) {
            return calculateMaxEndPosition(index);
        } else {
            if (index == 0 && getModel().isAddTabButtonShown()) {
                return tabContainerWidth - addTabButtonWidth;
            } else {
                int i = getModel().isAddTabButtonShown() ? index : index + 1;
                return tabContainerWidth - calculateAddTabButtonSpacing() -
                        (calculateTabSpacing() * i);
            }
        }
    }

    @Override
    protected final float calculateMaxEndPosition(final int index) {
        if (index == 0 && getModel().isAddTabButtonShown()) {
            return getModel().getCount() * calculateTabSpacing() + addTabButtonOffset;
        } else {
            int i = getModel().isAddTabButtonShown() ? index : index + 1;
            return (getModel().getCount() - i) * calculateTabSpacing();
        }
    }

    @Override
    protected final float calculateSuccessorPosition(@NonNull final AbstractItem item,
                                                     @NonNull final AbstractItem predecessor) {
        float predecessorPosition = predecessor.getTag().getPosition();

        if (predecessor instanceof AddTabItem) {
            return predecessorPosition - addTabButtonOffset - calculateTabSpacing();
        } else {
            return predecessorPosition - calculateTabSpacing();
        }
    }

    @Override
    protected final float calculatePredecessorPosition(@NonNull final AbstractItem item,
                                                       @NonNull final AbstractItem successor) {
        float successorPosition = successor.getTag().getPosition();
        return successorPosition + calculateTabSpacing();
    }

    @Override
    public final void onGlobalLayout() {
        AbstractItem[] items = calculateInitialItems(getModel().getFirstVisibleTabIndex(),
                getModel().getFirstVisibleTabPosition());
        AbstractItemIterator iterator = new InitialItemIterator(items, false, 0);
        AbstractItem item;

        while ((item = iterator.next()) != null) {
            if (item.isVisible()) {
                inflateAndUpdateView(item, null);
            }
        }

        Tab selectedTab = getModel().getSelectedTab();

        if (selectedTab != null) {
            inflateContent(selectedTab);
        }
    }

    @Nullable
    @Override
    public final ViewGroup getTabContainer() {
        return tabContainer;
    }

    @Nullable
    @Override
    public final Toolbar[] getToolbars() {
        return primaryToolbar != null && secondaryToolbar != null ?
                new Toolbar[]{primaryToolbar, secondaryToolbar} : null;
    }

    @Override
    public final void onSelectionChanged(final int previousIndex, final int index,
                                         @Nullable final Tab selectedTab,
                                         final boolean switcherHidden) {
        if (previousIndex != index) {
            contentContainer.removeAllViews();
            contentViewRecycler.removeAll();

            if (selectedTab != null) {
                inflateContent(selectedTab);
                tabViewRecycler.setComparator(
                        Collections.reverseOrder(new TabletItemComparator(getTabSwitcher())));
                int tabSpacing = calculateTabSpacing();
                int selectedItemIndex = index + (getModel().isAddTabButtonShown() ? 1 : 0);
                float referencePosition =
                        TabItem.create(getModel(), tabViewRecycler, index).getTag().getPosition();
                referencePosition = Math.max(
                        Math.min(calculateMaxEndPosition(selectedItemIndex), referencePosition),
                        calculateMinStartPosition(selectedItemIndex));
                AbstractItemIterator iterator =
                        new ItemIterator.Builder(getModel(), getTabViewRecycler()).create();
                AbstractItem item;

                while ((item = iterator.next()) != null) {
                    float position;

                    if (item.getIndex() == 0 && getModel().isAddTabButtonShown()) {
                        position = referencePosition + ((selectedItemIndex - 1) * tabSpacing) +
                                calculateTabSpacing() + addTabButtonOffset;
                    } else if (item.getIndex() == selectedItemIndex) {
                        position = referencePosition;
                    } else if (item.getIndex() < selectedItemIndex) {
                        position = referencePosition +
                                ((selectedItemIndex - item.getIndex()) * tabSpacing);
                    } else {
                        position = referencePosition -
                                ((item.getIndex() - selectedItemIndex) * tabSpacing);
                    }

                    Pair<Float, State> pair =
                            clipPosition(item.getIndex(), position, iterator.previous());
                    item.getTag().setPosition(pair.first);
                    item.getTag().setState(pair.second);
                    inflateOrRemoveView(item);
                }
            }
        }
    }

    @Override
    public final void onTabAdded(final int index, @NonNull final Tab tab,
                                 final int previousSelectedTabIndex, final int selectedTabIndex,
                                 final boolean switcherVisibilityChanged,
                                 @NonNull final Animation animation) {
        tab.addCallback(this);
        // TODO: Implement
    }

    @Override
    public final void onAllTabsAdded(final int index, @NonNull final Tab[] tabs,
                                     final int previousSelectedTabIndex, final int selectedTabIndex,
                                     @NonNull final Animation animation) {
        for (Tab tab : tabs) {
            tab.addCallback(this);
        }
        // TODO: Implement
    }

    @Override
    public final void onTabRemoved(final int index, @NonNull final Tab tab,
                                   final int previousSelectedTabIndex, final int selectedTabIndex,
                                   @NonNull final Animation animation) {
        tab.removeCallback(this);
        // TODO: Implement
    }

    @Override
    public final void onAllTabsRemoved(@NonNull final Tab[] tabs,
                                       @NonNull final Animation animation) {
        for (Tab tab : tabs) {
            tab.removeCallback(this);
        }
        // TODO: Implement
    }

    @Override
    public final void onPaddingChanged(final int left, final int top, final int right,
                                       final int bottom) {
        adaptTabContainerAndToolbarMargins();
    }

    @Override
    public void onTabBackgroundColorChanged(@Nullable final ColorStateList colorStateList) {
        adaptBorderColor();
    }

    @Override
    public final void onTabContentBackgroundColorChanged(@ColorInt final int color) {
        adaptTabContentBackgroundColor();
    }

    @Override
    public final void onAddTabButtonVisibilityChanged(final boolean visible) {
        // TODO: Implement
    }

    @Override
    public final void onTitleChanged(@NonNull final Tab tab) {

    }

    @Override
    public final void onIconChanged(@NonNull final Tab tab) {

    }

    @Override
    public final void onCloseableChanged(@NonNull final Tab tab) {

    }

    @Override
    public final void onCloseButtonIconChanged(@NonNull final Tab tab) {

    }

    @Override
    public final void onBackgroundColorChanged(@NonNull final Tab tab) {
        if (getModel().getSelectedTab() == tab) {
            adaptBorderColor();
        }
    }

    @Override
    public final void onContentBackgroundColorChanged(@NonNull final Tab tab) {
        if (getModel().getSelectedTab() == tab) {
            adaptTabContentBackgroundColor();
        }
    }

    @Override
    public final void onTitleTextColorChanged(@NonNull final Tab tab) {

    }

}