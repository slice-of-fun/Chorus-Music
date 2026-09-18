from PIL import Image, ImageFilter

def create_composite():
    # Load the mockups
    horizontal = Image.open("website/ss/3_mockup.png").convert("RGBA")
    vertical = Image.open("website/ss/2_mockup.png").convert("RGBA")

    hw, hh = horizontal.size
    vw, vh = vertical.size

    overlap = int(hw * 0.45) # 45% overlap to hide lyrics
    canvas_w = hw + vw - overlap
    canvas_h = max(hh, vh)
    
    canvas = Image.new("RGBA", (canvas_w, canvas_h), (0,0,0,0))
    
    # Paste horizontal first (background)
    # Align horizontal image to the bottom
    h_y = canvas_h - hh
    canvas.paste(horizontal, (0, h_y), horizontal)
    
    # Paste vertical (foreground)
    # Align vertical image to the bottom
    v_y = canvas_h - vh
    v_x = hw - overlap
    
    # Add some drop shadow to vertical image
    shadow = Image.new("RGBA", vertical.size, (0, 0, 0, 100))
    shadow.putalpha(vertical.split()[3])
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=20))
    
    canvas.paste(shadow, (v_x - 10, v_y + 10), shadow)
    canvas.paste(vertical, (v_x, v_y), vertical)

    # optionally crop any empty space top/bottom
    bbox = canvas.getbbox()
    if bbox:
        canvas = canvas.crop(bbox)

    canvas.save("website/ss/hero_mockup.png")
    print("Composite saved to website/ss/hero_mockup.png")

create_composite()
