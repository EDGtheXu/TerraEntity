package org.confluence.terraentity.api.event;

import net.minecraft.world.entity.LivingEntity;

import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import org.confluence.terraentity.entity.ai.goal.behavior.BTNode;

import java.util.function.Supplier;

/**
 * 重定向怪物AI的行为树节点，允许mod和kjs高度自定义行为
 */
public class RedirectBTEvent extends LivingEvent implements IReDirectable<BTNode>, IModBusEvent {

    BTNode reDirection;

    public RedirectBTEvent(LivingEntity entity) {
        super(entity);
    }

    @Override
    public void setRedirection(BTNode reDirection) {
        this.reDirection = reDirection;
    }

    @Override
    public BTNode getRedirectionOrDefault(Supplier<BTNode> defaultValue) {
        if(this.reDirection == null) {
            return defaultValue.get();
        }
        return this.reDirection;
    }
}
