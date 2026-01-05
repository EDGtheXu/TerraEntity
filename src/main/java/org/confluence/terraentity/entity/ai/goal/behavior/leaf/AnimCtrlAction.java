package org.confluence.terraentity.entity.ai.goal.behavior.leaf;

import org.confluence.terraentity.api.entity.ISharedFlagControllerHolder;
import org.confluence.terraentity.entity.util.SharedFlagController;
import software.bernie.geckolib.animatable.GeoEntity;

/**
 * geo动画状态触发器
 */
public class AnimCtrlAction<T extends GeoEntity & ISharedFlagControllerHolder> extends SyncFlagAction<T> {

    final String controllerName;
    final String animationName;
    public AnimCtrlAction(T entity,
                          String controllerName,
                          String animationName,
                          SharedFlagController.SharedFlag sharedFlag,
                          boolean isEnable) {
        super(entity, sharedFlag, isEnable);

        this.controllerName = controllerName;
        this.animationName = animationName;
    }

    @Override
    public void start() {
        super.start();
        if(isEnable) {
            entity.triggerAnim(controllerName, animationName);
        }
    }
}
