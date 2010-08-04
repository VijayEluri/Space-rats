/**
 *                      fake CIA timer for sidplay1 environment modes
 *                      ---------------------------------------------
 *  begin                : Wed Jun 7 2000
 *  copyright            : (C) 2000 by Simon White
 *  email                : s_a_white@email.com
 *
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 * @author Ken Händel
 *
 */
package libsidplay.components.mos6526;

import static libsidplay.common.SIDEndian.endian_16hi8;
import static libsidplay.common.SIDEndian.endian_16lo8;
import static libsidplay.Config.S_A_WHITE_EMAIL;
import libsidplay.common.C64Env;
import libsidplay.common.Event;
import libsidplay.common.Event.event_phase_t;
import libsidplay.common.IComponent;
import libsidplay.common.IEventContext;

public class SID6526 implements IComponent {

	/**
	 * Resolve multiple inheritance
	 */
	protected Event event = new Event("CIA Timer A") {

		public void event() {
			SID6526.this.event();
		}
	};


	/**
	 * Optional information
	 */
	private static final String credit = "*SID6526 (SIDPlay1 Fake CIA) Emulation:"
			+ "\tCopyright (C) 2001 Simon White <" + S_A_WHITE_EMAIL + ">";

	private C64Env m_env;

	private IEventContext m_eventContext;

	private long /* event_clock_t */m_accessClk;

	private event_phase_t m_phase;

	private short /* uint8_t */regs[] = new short[0x10];

	/**
	 * Timer A Control Register
	 */
	private short /* uint8_t */cra;

	private int /* uint_least16_t */ta_latch;

	/**
	 * Current count (reduces to zero)
	 */
	private int /* uint_least16_t */ta;

	private long /* uint_least32_t */rnd;

	private int /* uint_least16_t */m_count;

	/**
	 * Prevent code changing CIA.
	 */
	private boolean locked;

	public SID6526(C64Env env) {
		m_env = (env);
		m_eventContext = (m_env.context());
		m_phase = (event_phase_t.EVENT_CLOCK_PHI1);
		rnd = (0);
		clock(0xffff);
		reset(false);
	}

	//
	// Common:
	//
	
	public void reset() {
		reset(false);
	}

	public void reset(boolean seed) {
		locked = false;
		ta = ta_latch = m_count;
		cra = 0;
		// Initialise random number generator
		if (seed)
			rnd = 0;
		else
			rnd += System.nanoTime() & 0xff;
		m_accessClk = 0;
		// Remove outstanding events
		event.cancel ();
	}

	public short /* uint8_t */read(short /* uint8_t */addr) {
		if (addr > 0x0f)
			return 0;
	
		switch (addr) {
		case 0x04:
		case 0x05:
		case 0x11:
		case 0x12:
			rnd = rnd * 13 + 1;
			return (short /* uint8_t */) (rnd >> 3);
		default:
			return regs[addr];
		}
	}

	public void write(short /* uint_least8_t */addr, short /* uint8_t */data) {
		if (addr > 0x0f)
			return;
	
		regs[addr] = data;
	
		if (locked)
			return; // Stop program changing time interval
	
		{ // Sync up timer
			long /* event_clock_t */cycles;
			cycles = m_eventContext.getTime(m_accessClk, m_phase);
			m_accessClk += cycles;
			ta -= cycles;
			if (ta == 0)
				event();
		}
	
		switch (addr) {
		case 0x4:
			ta_latch = endian_16lo8(ta_latch, data);
			break;
		case 0x5:
			ta_latch = endian_16hi8(ta_latch, data);
			if ((cra & 0x01) == 0) // Reload timer if stopped
				ta = ta_latch;
			break;
		case 0x0e:
			cra = (short) (data | 0x01);
			if ((data & 0x10) != 0) {
				cra &= (~0x10 & 0xff);
				ta = ta_latch;
			}
			event.schedule (m_eventContext, (long /* event_clock_t */) ta + 3, m_phase);
			break;
		default:
			break;
		}
	}

	public final String credits() {
		return credit;
	}

	public final String error() {
		return "";
	}

	//
	// Specific:
	//
	
	public void event() {
		// Timer Modes
		m_accessClk = m_eventContext.getTime(m_phase);
		ta = ta_latch;
		event.schedule (m_eventContext, (long /* event_clock_t */) ta + 1, m_phase);
		m_env.interruptIRQ(true);
	}

	public void clock(int /* uint_least16_t */count) {
		m_count = count;
	}

	public void lock() {
		locked = true;
	}

}
