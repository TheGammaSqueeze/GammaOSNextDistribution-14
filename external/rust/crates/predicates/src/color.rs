use std::io::Write;
use termcolor::{Color, ColorSpec, WriteColor};

#[derive(Clone, Debug, Default)]
pub(crate) struct Palette {
    description: ColorSpec,
    var: ColorSpec,
    expected: ColorSpec,
}

impl Palette {
    pub(crate) fn new(alternate: bool) -> Self {
        if alternate && cfg!(feature = "color") {
            Self {
                description: ColorSpec::new()
                    .set_fg(Some(Color::Blue))
                    .set_bold(true)
                    .clone(),
                var: ColorSpec::new()
                    .set_fg(Some(Color::Red))
                    .set_bold(true)
                    .clone(),
                expected: ColorSpec::new()
                    .set_fg(Some(Color::Green))
                    .set_bold(true)
                    .clone(),
            }
        } else {
            Self::plain()
        }
    }

    pub(crate) fn plain() -> Self {
        Self {
            description: Default::default(),
            var: Default::default(),
            expected: Default::default(),
        }
    }

    pub(crate) fn description<D: std::fmt::Display>(&self, display: D) -> Styled<D> {
        Styled::new(display, self.description.clone())
    }

    pub(crate) fn var<D: std::fmt::Display>(&self, display: D) -> Styled<D> {
        Styled::new(display, self.var.clone())
    }

    pub(crate) fn expected<D: std::fmt::Display>(&self, display: D) -> Styled<D> {
        Styled::new(display, self.expected.clone())
    }
}

#[derive(Debug)]
pub(crate) struct Styled<D> {
    display: D,
    style: ColorSpec,
}

impl<D: std::fmt::Display> Styled<D> {
    pub(crate) fn new(display: D, style: ColorSpec) -> Self {
        Self { display, style }
    }
}

impl<D: std::fmt::Display> std::fmt::Display for Styled<D> {
    #[inline]
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        if f.alternate() {
            let mut buf = termcolor::Buffer::ansi();
            buf.set_color(&self.style).unwrap();
            write!(&mut buf, "{}", &self.display).unwrap();
            buf.reset().unwrap();
            write!(f, "{}", String::from_utf8(buf.into_inner()).unwrap())
        } else {
            self.display.fmt(f)
        }
    }
}
