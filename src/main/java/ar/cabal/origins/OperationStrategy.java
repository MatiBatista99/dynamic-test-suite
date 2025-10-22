package ar.cabal.origins;

import ar.cabal.dtos.Case;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;

import java.text.ParseException;

public interface OperationStrategy {
    ISOMsg build(ISOMsg msg, Case c, ISOPackager packager) throws ISOException, ParseException;
}

