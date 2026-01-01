package org.confluence.terraentity.entity.ai.goal.behavior.leaf;

import org.confluence.terraentity.api.entity.ISharedFlagControllerHolder;
import org.confluence.terraentity.entity.ai.goal.behavior.BTNode;
import org.confluence.terraentity.entity.util.SharedFlagController;
import software.bernie.geckolib.animatable.GeoEntity;

/**
 * geo动画状态触发器
 */
public class AnimCtrlAction<T extends GeoEntity & ISharedFlagControllerHolder> extends BTNode {
    final T entity;
    final SharedFlagController.SharedFlag sharedFlag;
    final boolean isEnable;
    final String controllerName;
    final String animationName;
    public AnimCtrlAction(T entity,
                          String controllerName,
                          String animationName,
                          SharedFlagController.SharedFlag sharedFlag,
                          boolean isEnable) {
        this.entity = entity;
        this.sharedFlag = sharedFlag;
        this.isEnable = isEnable;
        this.controllerName = controllerName;
        this.animationName = animationName;
    }

    @Override
    public BTStatus execute() {
        return BTStatus.SUCCESS;
    }

    @Override
    public void start() {
        super.start();
        entity.getSharedFlagController().setFlag(sharedFlag, isEnable);
        entity.triggerAnim(controllerName, animationName);
    }
}
