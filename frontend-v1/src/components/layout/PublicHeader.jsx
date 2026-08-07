// src/components/Header.jsx
//
// SHELL — working skeleton only. No styling, no routes, no business logic.
// Keeps the mechanics you'll actually need to wire up later:
//   - responsive breakpoint detection
//   - dropdown open/close + click-outside-to-close
//   - auth vs guest branch (swap in real auth state)
//
// Fill in the empty <div> blocks with real content/components.

import React, { useState, useRef, useEffect } from "react";

const MOBILE_BREAKPOINT = 640;

const Navbar = () => {
  // ── Auth placeholder — replace with real auth state (redux/context/etc.) ──
  const isAuthenticated = false;

  // ── Responsive detection ──
  const [isMobile, setIsMobile] = useState(false);
  useEffect(() => {
    const check = () => setIsMobile(window.innerWidth < MOBILE_BREAKPOINT);
    check();
    window.addEventListener("resize", check);
    return () => window.removeEventListener("resize", check);
  }, []);

  // ── Dropdown open/close + click-outside ──
  const [menuOpen, setMenuOpen] = useState(false);
  const dropdownRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <header>
      <div>
        {/* ── LEFT: logo / brand slot ── */}
        <div>{/* logo goes here */}</div>

        {/* ── CENTER: nav links / search / whatever ── */}
        <div>{/* nav content goes here */}</div>

        {/* ── RIGHT: auth-aware actions ── */}
        <div>
          {isAuthenticated ? (
            <div ref={dropdownRef}>
              <button onClick={() => setMenuOpen((prev) => !prev)}>
                {/* user pill / avatar trigger goes here */}
              </button>

              {menuOpen && (
                <div>
                  {/* dropdown menu items go here */}
                </div>
              )}
            </div>
          ) : (
            <div>{/* login/signup buttons go here */}</div>
          )}
        </div>
      </div>

      {/* ── Mobile-only slot (drawer trigger, condensed actions, etc.) ── */}
      {isMobile && <div>{/* mobile-specific content goes here */}</div>}
    </header>
  );
};

export default Navbar;