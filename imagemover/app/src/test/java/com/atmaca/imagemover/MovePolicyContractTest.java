package com.atmaca.imagemover;

import org.junit.Test;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MovePolicyContractTest {
    private Object policy() throws Exception {
        Class<?> type = Class.forName("com.atmaca.imagemover.MovePolicy");
        return type.getDeclaredConstructor().newInstance();
    }

    @Test
    public void targetPathIsPictures1907() throws Exception {
        Object policy = policy();
        Method method = policy.getClass().getMethod("targetRelativePath");
        assertEquals("Pictures/1907/", method.invoke(policy));
    }

    @Test
    public void sameNameDestinationIsReplaced() throws Exception {
        Object policy = policy();
        Method method = policy.getClass().getMethod("replaceSameNameDestination");
        assertTrue((Boolean) method.invoke(policy));
    }

    @Test
    public void sourceIsDeletedOnlyAfterSuccessfulCopy() throws Exception {
        Object policy = policy();
        Method method = policy.getClass().getMethod("deleteSourceOnlyAfterSuccessfulCopy");
        assertTrue((Boolean) method.invoke(policy));
    }
}
