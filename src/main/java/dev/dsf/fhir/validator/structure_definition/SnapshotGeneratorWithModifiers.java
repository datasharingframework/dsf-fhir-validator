package dev.dsf.fhir.validator.structure_definition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.hl7.fhir.r4.model.StructureDefinition;
import org.springframework.beans.factory.InitializingBean;

public class SnapshotGeneratorWithModifiers implements SnapshotGenerator, InitializingBean
{
	public static final StructureDefinitionModifier CLOSED_TYPE_SLICING_REMOVER = new ClosedTypeSlicingRemover();
	public static final StructureDefinitionModifier IDENTIFIER_REMOVER = new IdentifierRemover();
	public static final StructureDefinitionModifier SLICE_MIN_FIXER = new SliceMinFixer();

	private final SnapshotGenerator delegate;
	private final List<StructureDefinitionModifier> structureDefinitionModifiers = new ArrayList<>();

	public SnapshotGeneratorWithModifiers(SnapshotGenerator delegate)
	{
		this(delegate, Arrays.asList(CLOSED_TYPE_SLICING_REMOVER, IDENTIFIER_REMOVER, SLICE_MIN_FIXER));
	}

	public SnapshotGeneratorWithModifiers(SnapshotGenerator delegate,
			Collection<? extends StructureDefinitionModifier> structureDefinitionModifiers)
	{
		this.delegate = delegate;

		if (structureDefinitionModifiers != null)
			this.structureDefinitionModifiers.addAll(structureDefinitionModifiers);
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(delegate, "delegate");
	}

	@Override
	public SnapshotWithValidationMessages generateSnapshot(StructureDefinition differential)
	{
		return delegate.generateSnapshot(modify(differential));
	}

	@Override
	public SnapshotWithValidationMessages generateSnapshot(StructureDefinition differential,
			String baseAbsoluteUrlPrefix)
	{
		return delegate.generateSnapshot(modify(differential), baseAbsoluteUrlPrefix);
	}

	private StructureDefinition modify(StructureDefinition differential)
	{
		if (differential == null)
			return null;

		for (StructureDefinitionModifier mod : structureDefinitionModifiers)
			differential = mod.modify(differential);

		return differential;
	}
}
