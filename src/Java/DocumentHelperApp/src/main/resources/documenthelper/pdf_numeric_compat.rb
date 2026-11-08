# frozen_string_literal: true

# prawn-svg 0.34.2 interpolates Float values directly for SVG textLength.
# Tiny spacing values become exponential notation, which is illegal in PDF
# content streams. Acrobat stops drawing at that command, although PDFium
# tolerates it. Use the same decimal serializer as Prawn's own text operators.
module DocumentHelper
  module PdfNumericCompat
    def add_content(content)
      if (match = /\A([-+]?\d+(?:\.\d+)?[eE][-+]?\d+) (Tc|Tz)\z/.match(content))
        content = "#{PDF::Core.real(match[1].to_f)} #{match[2]}"
      end
      super(content)
    end
  end
end

Prawn::Document.prepend DocumentHelper::PdfNumericCompat
