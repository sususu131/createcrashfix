package com.example.createcrashfixcrashfix;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;

import java.util.List;
import java.util.Set;

public final class CreateCrashFixCrashFixService implements ITransformationService {
    @Override
    public String name() {
        return "createcrashfixcrashfix";
    }

    @Override
    public void initialize(IEnvironment environment) {
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List<ITransformer> transformers() {
        return List.of(new CreateCrashFixCrashFixTransformer());
    }
}
